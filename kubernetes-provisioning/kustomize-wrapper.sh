#!/bin/bash
cat <&0 > chart.yaml
kustomize build . && rm chart.yaml