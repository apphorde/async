FROM eclipse-temurin:25-jdk-jammy

ARG ANDROID_CMDLINE_TOOLS_VERSION=13114758

ENV ANDROID_HOME=/opt/android-sdk \
    ANDROID_SDK_ROOT=/opt/android-sdk \
    PATH=/opt/android-sdk/cmdline-tools/latest/bin:/opt/android-sdk/platform-tools:/opt/android-sdk/emulator:/opt/android-sdk/tools/bin:$PATH

RUN apt-get update \
    && apt-get install --no-install-recommends -y ca-certificates unzip wget \
    && rm -rf /var/lib/apt/lists/* \
    && mkdir -p "${ANDROID_SDK_ROOT}/cmdline-tools" \
    && wget -q "https://dl.google.com/android/repository/commandlinetools-linux-${ANDROID_CMDLINE_TOOLS_VERSION}_latest.zip" -O /tmp/android-commandline-tools.zip \
    && mkdir -p /tmp/android-commandline-tools \
    && unzip -q /tmp/android-commandline-tools.zip -d /tmp/android-commandline-tools \
    && mv /tmp/android-commandline-tools/cmdline-tools "${ANDROID_SDK_ROOT}/cmdline-tools/latest" \
    && rm -rf /tmp/android-commandline-tools /tmp/android-commandline-tools.zip \
    && yes | sdkmanager --licenses >/dev/null \
    && sdkmanager "platform-tools" "platforms;android-34" "build-tools;34.0.0" "build-tools;35.0.0" \
    && rm -rf "${ANDROID_SDK_ROOT}/.temp" /root/.cache

WORKDIR /workspace

ENTRYPOINT ["/bin/sh"]
