#!/usr/bin/env bash
# ============================================================================
# Maven 包装脚本
#
# 为什么需要这个脚本：
#   1. 本机 PATH 中的 java 默认是 JDK 8，而 Spring Boot 3.x 需要 JDK 17
#   2. MAVEN_HOME 环境变量指向了外层目录（apache-maven-3.9.16-bin），
#      缺少 boot/plexus-classworlds.jar，直接执行 mvn 会报
#      "找不到或无法加载主类 org.codehaus.plexus.classworlds.launcher.Launcher"
#
# 解决方案：
#   本脚本直接调用 java 运行 Maven 的 Launcher，绕过 mvn 脚本对环境变量的依赖
#
# 用法：
#   ./mvn.sh clean compile
#   ./mvn.sh spring-boot:run
#   ./mvn.sh package -DskipTests
# ============================================================================

# JDK 17 路径（Spring Boot 3.x 最低要求）
export JAVA_HOME="C:/Program Files/Java/jdk-17"

# Maven 实际解压目录（注意：外层 apache-maven-3.9.16-bin 是多余的包装层）
export MAVEN_HOME="E:/repmvn/apache-maven-3.9.16-bin/apache-maven-3.9.16"

# 项目根目录（脚本所在目录的上一级）
PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

echo "JAVA_HOME  = ${JAVA_HOME}"
echo "MAVEN_HOME = ${MAVEN_HOME}"
echo "PROJECT    = ${PROJECT_DIR}"
echo "----------------------------------------"

"${JAVA_HOME}/bin/java" \
  -classpath "${MAVEN_HOME}/boot/plexus-classworlds-2.11.0.jar" \
  -Dclassworlds.conf="${MAVEN_HOME}/bin/m2.conf" \
  -Dmaven.home="${MAVEN_HOME}" \
  -Dmaven.conf="${MAVEN_HOME}/conf" \
  -Dlibrary.jansi.path="${MAVEN_HOME}/lib/jansi-native" \
  -Dmaven.multiModuleProjectDirectory="${PROJECT_DIR}" \
  org.codehaus.plexus.classworlds.launcher.Launcher "$@"

exit $?
