#!/bin/sh

exec sh -ex \
  /src/govuk-tax-0.0.1-SNAPSHOT/start \
  -Dhttp.port=8080 \
  -Dapplication.log=INFO \
  -Dlogger.resource=/govuk-tax-logger.xml \
  -Dconfig.resource=/application.conf.conf \
  $HMRC_CONFIG
