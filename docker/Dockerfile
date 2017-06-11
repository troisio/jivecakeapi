FROM centos:7

ENV TERM xterm
ENV SOURCE_DIRECTORY /root/jivecakeapi
ENV SETTINGS_FILE /root/settings.yml
ENV JAVA_HOME /usr/java/jdk1.8.0_131
ENV GRADLE_HOME /opt/gradle-3.2.1
ENV GOOGLE_APPLICATION_CREDENTIALS /root/gcp-credentials.json

ADD init.sh /init.sh

RUN yum -y update && \
    yum install -y man-pages man nano wget curl git-all unzip && \

  wget -c --header "Cookie: oraclelicense=accept-securebackup-cookie" http://download.oracle.com/otn-pub/java/jdk/8u131-b11/d54c1d3a095b4ff2b6607d096fa80163/jdk-8u131-linux-x64.rpm && \
  yum -y install jdk-8u131-linux-x64.rpm && \
  rm jdk-8u131-linux-x64.rpm && \

  wget --directory-prefix=/opt https://services.gradle.org/distributions/gradle-3.2.1-bin.zip && \
  unzip -d /opt /opt/gradle-3.2.1-bin.zip && \
  rm /opt/gradle-3.2.1-bin.zip && \
  update-alternatives --install /usr/bin/gradle gradle $GRADLE_HOME/bin/gradle 1 && \

  git clone -b master https://github.com/troisio/jivecakeapi.git $SOURCE_DIRECTORY && \
  cd $SOURCE_DIRECTORY && \
  gradle shadowJar && \
  rm -rf $SOURCE_DIRECTORY && \
  
  chmod +x /init.sh && \

  yum clean all

CMD /init.sh