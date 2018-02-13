FROM centos:7

ENV TERM xterm
ENV SOURCE_DIRECTORY /root/jivecakeapi
ENV SETTINGS_FILE /root/settings.yml
ENV GRADLE_HOME /opt/gradle-4.4.1
ENV JAVA_HOME /usr/lib/jvm/java-1.8.0-openjdk-1.8.0.161-0.b14.el7_4.x86_64
ENV GOOGLE_APPLICATION_CREDENTIALS /root/gcp-credentials.json

ADD init.sh /init.sh

RUN yum -y update && \
    yum install -y man-pages man nano wget curl git-all unzip java-1.8.0-openjdk-devel && \

  wget --directory-prefix=/opt https://services.gradle.org/distributions/gradle-4.4.1-bin.zip && \
  unzip -d /opt /opt/gradle-4.4.1-bin.zip && \
  rm /opt/gradle-4.4.1-bin.zip && \
  update-alternatives --install /usr/bin/gradle gradle $GRADLE_HOME/bin/gradle 1 && \

  git clone -b master https://github.com/troisio/jivecakeapi.git $SOURCE_DIRECTORY && \
  cd $SOURCE_DIRECTORY && \
  gradle shadowJar && \
  rm -rf $SOURCE_DIRECTORY && \
  
  chmod +x /init.sh && \

  yum clean all

CMD /init.sh