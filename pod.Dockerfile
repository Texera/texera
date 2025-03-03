# Use to build image of solely Texera's backend

FROM sbtscala/scala-sbt:eclipse-temurin-jammy-11.0.17_8_1.9.3_2.13.11

WORKDIR /core/amber
COPY core/amber .
RUN sbt clean package
RUN apt-get update
RUN apt-get install -y netcat unzip python3-pip
RUN pip3 install python-lsp-server python-lsp-server[websockets]
RUN pip3 install -r requirements.txt
RUN pip3 install -r operator-requirements.txt

WORKDIR /core
COPY core/scripts ./scripts
# Add .git for runtime calls to jgit from OPversion
COPY .git ../.git

COPY diabetes.csv ./diabetes.csv

RUN scripts/build-docker.sh

CMD ["scripts/deploy-docker-trap.sh"]

EXPOSE 8080
