#!/usr/bin/env bash
# Internal target runner for ./gradlew; requires Bash and the standard awk utility.
set -eo pipefail

fail() { printf '%s\n' "$*" >&2; exit 2; }
repository_root=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd -P)
selected_version=
selected_java_home=
show_target=false
gradle_arguments=()

while (($#)); do
    argument=$1
    shift
    case "$argument" in
        --) gradle_arguments+=("$@"); break ;;
        -Version|-Version=*|-PbuildVersion|-PbuildVersion=*|-JavaHome|-JavaHome=*)
            option=${argument%%=*}
            if [[ $argument == *=* ]]; then
                value=${argument#*=}
            else
                (($#)) && [[ $1 != -* ]] || fail "$option requires a value."
                value=$1
                shift
            fi
            [[ -n ${value//[[:space:]]/} ]] || fail "$option requires a value."
            if [[ $option == -JavaHome ]]; then
                [[ -z $selected_java_home ]] || fail '-JavaHome must be specified only once.'
                selected_java_home=$value
            else
                [[ -z $selected_version ]] || fail '-Version must be specified only once.'
                selected_version=$value
            fi
            ;;
        -ShowTarget) show_target=true ;;
        *) gradle_arguments+=("$argument") ;;
    esac
done

# The checked-in catalog has one scalar field per line and no nested target fields.
# Read those fields without evaluating their contents as shell code.
profile=$(awk -v requested="$selected_version" '
    function value(line) {
        sub(/^[^:]*:[[:space:]]*"?/, "", line)
        sub(/"?[,[:space:]]*$/, "", line)
        return line
    }
    /"defaultVersion"[[:space:]]*:/ { defaultVersion = value($0) }
    /"targets"[[:space:]]*:/ { inTargets = 1; next }
    inTargets && /^[[:space:]]*"[^"]+"[[:space:]]*:[[:space:]]*\{/ {
        target = $0
        sub(/^[[:space:]]*"/, "", target)
        sub(/".*/, "", target)
        available = available (available ? ", " : "") target
    }
    inTargets && /"javaVersion"[[:space:]]*:/ { java[target] = value($0) }
    inTargets && /"projectDirectory"[[:space:]]*:/ { directory[target] = value($0) }
    inTargets && /"gradleVersion"[[:space:]]*:/ { gradle[target] = value($0) }
    inTargets && /"status"[[:space:]]*:/ { status[target] = value($0) }
    END {
        selected = requested ? requested : defaultVersion
        if (!(selected in java)) {
            printf "Unsupported Minecraft version %s. Available: %s.\n", selected, available > "/dev/stderr"
            exit 2
        }
        if (!java[selected] || !directory[selected] || !gradle[selected] || !status[selected]) {
            print "Incomplete Minecraft target catalog." > "/dev/stderr"
            exit 2
        }
        printf "%s\t%s\t%s\t%s\t%s\n", selected, java[selected], directory[selected], gradle[selected], status[selected]
    }
' "$repository_root/gradle/minecraft-targets.json") || exit 2
IFS=$'\t' read -r selected_version java_version relative_directory gradle_version status <<< "$profile"
project_directory=$(cd -- "$repository_root/$relative_directory" && pwd -P)
wrapper="$project_directory/gradlew"

# Values of Gradle options are not task names.
has_task=false
skip_value=false
for argument in "${gradle_arguments[@]}"; do
    if $skip_value; then skip_value=false; continue; fi
    case "$argument" in
        -b|--build-file|-c|--settings-file|-g|--gradle-user-home|-I|--init-script|-p|--project-dir|-x|--exclude-task|--include-build|--console|--warning-mode|--max-workers|--priority|--project-cache-dir|-D|-P)
            skip_value=true ;;
        -*) ;;
        *) has_task=true; break ;;
    esac
done
if ! $has_task; then gradle_arguments=(build "${gradle_arguments[@]}"); fi

json_string() {
    local value=$1
    value=${value//\\/\\\\}
    value=${value//\"/\\\"}
    value=${value//$'\n'/\\n}
    value=${value//$'\r'/\\r}
    value=${value//$'\t'/\\t}
    value=${value//$'\b'/\\b}
    value=${value//$'\f'/\\f}
    printf '"%s"' "$value"
}
if $show_target; then
    printf '{\n  "minecraftVersion": '; json_string "$selected_version"
    printf ',\n  "javaVersion": %s,\n  "gradleVersion": ' "$java_version"; json_string "$gradle_version"
    printf ',\n  "status": '; json_string "$status"
    printf ',\n  "projectDirectory": '; json_string "$project_directory"
    printf ',\n  "wrapper": '; json_string "$wrapper"
    printf ',\n  "gradleArguments": ['
    separator=
    for argument in "${gradle_arguments[@]}"; do
        printf '%s' "$separator"; json_string "$argument"; separator=', '
    done
    printf ']\n}\n'
    exit 0
fi

[[ -f $wrapper ]] || fail "Gradle wrapper missing for Minecraft $selected_version: $wrapper"
java_home=${selected_java_home:-${JAVA_HOME:-}}
if [[ -n $java_home ]]; then
    java_executable="$java_home/bin/java"
    [[ -x $java_executable ]] || fail "Java executable missing: $java_executable"
else
    java_executable=$(command -v java) || fail "Minecraft $selected_version requires JDK $java_version. Set JAVA_HOME or pass -JavaHome <JDK directory>."
fi
java_output=$("$java_executable" -version 2>&1) || fail "Could not determine Java version from $java_executable."
java_major=$(printf '%s\n' "$java_output" | sed -n 's/.*version "\([0-9][0-9]*\).*/\1/p' | head -n 1)
[[ -n $java_major ]] || fail "Could not determine Java version from $java_executable."
[[ $java_major == "$java_version" ]] || fail "Minecraft $selected_version requires JDK $java_version; found JDK $java_major. Set JAVA_HOME or pass -JavaHome <JDK directory>."

printf 'Building Minecraft %s with JDK %s / Gradle %s.\n' "$selected_version" "$java_major" "$gradle_version"
if [[ $status != release ]]; then
    printf 'Minecraft %s is %s; the port is not a verified release.\n' "$selected_version" "$status" >&2
fi
if [[ -n $java_home ]]; then
    # Keep a caller-relative Java path valid after changing to the selected project.
    JAVA_HOME=$(cd -- "$java_home" && pwd -P)
    export JAVA_HOME
fi
cd -- "$project_directory"
if [[ $project_directory == "$repository_root" ]]; then export ANTE_INTERNAL_GRADLE_WRAPPER=1; fi
exec sh "$wrapper" "${gradle_arguments[@]}"
