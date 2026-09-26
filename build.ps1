$gradlewPath = "F:\mtr-ante\gradlew.bat"
Start-Process -FilePath "$gradlewPath" -ArgumentList "build", '-PbuildVersion="1.18.2"', "-Dorg.gradle.internal.http.socketTimeout=2000", "-Dorg.gradle.internal.http.connectionTimeout=2000", "--warning-mode all" -NoNewWindow -Wait
# "-Dorg.gradle.internal.http.socketTimeout=200000", "-Dorg.gradle.internal.http.connectionTimeout=200000", 