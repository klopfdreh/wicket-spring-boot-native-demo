# AMD64
#FROM --platform=linux/amd64 redhat/ubi10-minimal:latest AS builder

# ARM64
FROM --platform=linux/arm64 redhat/ubi10-minimal:latest AS builder

ARG RUNTIMEUSER=1001

ENV ARTIFACT_JAR_PATTERN=*-exec.jar
ENV MAIN_CLASS=wicket.tests.WicketNativeApplication
ENV BINARY_NAME=wicketnative

# AMD64
#ENV NIK_TAR_GZ=bellsoft-liberica-vm-openjdk25.0.4.1+2-25.0.4.1+1-linux-amd64.tar.gz
#ENV NIK_DOWNLOAD_URL=https://github.com/bell-sw/LibericaNIK/releases/download/25.0.4.1%2B1-25.0.4.1%2B2/${NIK_TAR_GZ}
#ENV NIK_FOLDER=bellsoft-liberica-vm-openjdk25-25.0.4.1
#ENV NIK_CHECKSUM=bfe1f9d2a3645c50e1b682fd3704da86c4a2193a

# ARM64
ENV NIK_TAR_GZ=bellsoft-liberica-vm-openjdk25.0.4.1+2-25.0.4.1+1-linux-aarch64.tar.gz
ENV NIK_DOWNLOAD_URL=https://github.com/bell-sw/LibericaNIK/releases/download/25.0.4.1%2B1-25.0.4.1%2B2/${NIK_TAR_GZ}
ENV NIK_FOLDER=bellsoft-liberica-vm-openjdk25-25.0.4.1
ENV NIK_CHECKSUM=302cdab6b8676fbd3e6451e9e6dcc0204bb31d2c

USER root

WORKDIR /Library/Java/LibericaNativeImageKit/

# Install required tools and download Liberica Native Image Kit
RUN microdnf --setopt=install_weak_deps=0 --setopt=tsflags=nodocs install -y tar g++ make zlib-devel gzip findutils && \
    microdnf clean all && \
    mkdir -p /Library/Java/LibericaNativeImageKit/ && \
    curl -OL ${NIK_DOWNLOAD_URL} && \
    echo "'$(sha1sum ${NIK_TAR_GZ})' checked against '${NIK_CHECKSUM}  ${NIK_TAR_GZ}'" && \
    if [ $(sha1sum ${NIK_TAR_GZ}) != `echo ${NIK_CHECKSUM}  ${NIK_TAR_GZ}` ]; then exit 1; fi && \
    tar -zxvf ./${NIK_TAR_GZ} && \
    rm ./${NIK_TAR_GZ} && \
    mkdir -p /native-image-build/

ENV NIK_HOME=/Library/Java/LibericaNativeImageKit/${NIK_FOLDER}/
ENV JAVA_HOME=/Library/Java/LibericaNativeImageKit/${NIK_FOLDER}/
ENV PATH="$PATH:/Library/Java/LibericaNativeImageKit/${NIK_FOLDER}/bin/"

# Build native image
COPY ./target/${ARTIFACT_JAR_PATTERN} ./InitializeAtBuildTime ./InitializeAtRunTime /native-image-build/
WORKDIR /native-image-build/
RUN jar -xvf ${ARTIFACT_JAR_PATTERN} && \
    native-image \
    --no-fallback \
    -march=native \
    -Djavax.net.ssl.trustStore=/etc/pki/ca-trust/extracted/java/cacerts \
    --initialize-at-build-time=$(tr '\n' ',' < ./InitializeAtBuildTime) \
    --initialize-at-run-time=$(tr '\n' ',' < ./InitializeAtRunTime) \
    --enable-https \
    --enable-url-protocols=https \
    --install-exit-handlers \
    -cp .:BOOT-INF/classes:$(find BOOT-INF/lib | tr '\n' ':') ${MAIN_CLASS} \
    -o ${BINARY_NAME}

# Create the final runtime image
# AMD64
#FROM --platform=linux/amd64 redhat/ubi10-minimal:latest

# ARM64
FROM --platform=linux/arm64 redhat/ubi10-minimal:latest

ARG RUNTIMEUSER=1001

ENV BINARY_NAME=wicketnative

USER root

# Copy the native image from the builder stage
COPY --from=builder /native-image-build/${BINARY_NAME} /native-image/

WORKDIR /native-image/

USER ${RUNTIMEUSER}

ENTRYPOINT [ "bash", "-c", "/native-image/${BINARY_NAME}"]