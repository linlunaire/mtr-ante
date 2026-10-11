#!/usr/bin/env bash

MOD_VERSION=$(grep "mod_version" gradle.properties | cut -d'=' -f2 | tr -d '\r')
echo "当前版本: ${MOD_VERSION}"

awk -v version="${MOD_VERSION}" '
BEGIN { capture = 0; found = 0; idx=0 }
/^## / {
    if ($2 == version) {
        found = 1
        next
    } else if (found) {
        exit
    }
}
found && /^更新内容:/ { capture = 1; next }
capture {
    if (/^[[:space:]]*([0-9]+\.)/ || /^[[:space:]]*- /) {
        lines[++idx] = $0
    } else if (/^[[:space:]]*$/ && capture_started == 1) {
        lines[++idx] = $0
    } else if (!/^[[:space:]]*$/){
        capture_started = 1
        lines[++idx] = $0
    }
}
END {
    # 找到最后一个非空行的索引
    last_non_empty = 0
    for (i = 1; i <= idx; i++) {
        if (lines[i] != "") {
            last_non_empty = i
        }
    }
    # 输出到最后一个非空行
    for (i = 1; i <= last_non_empty; i++) {
        print lines[i]
    }
}' ./docs/changelog.md | sed 's/\r$//' > extracted_changelog.md

echo "提取完成，请查看 extracted_changelog.md 文件"