{{- define "ms.name" -}}
{{- default .Release.Name .Values.nameOverride | trunc 63 | trimSuffix "-" -}}
{{- end -}}

{{- define "ms.labels" -}}
app.kubernetes.io/name: {{ include "ms.name" . }}
app.kubernetes.io/version: {{ .Values.image.tag | default .Chart.AppVersion | quote }}
app.kubernetes.io/part-of: congthanhapp-ecommerce
app.kubernetes.io/managed-by: {{ .Release.Service }}
helm.sh/chart: {{ .Chart.Name }}-{{ .Chart.Version }}
{{- end -}}

{{- define "ms.selector" -}}
app.kubernetes.io/name: {{ include "ms.name" . }}
{{- end -}}

{{- define "ms.image" -}}
{{- if .Values.image.digest -}}
{{ .Values.image.repository }}@{{ .Values.image.digest }}
{{- else -}}
{{ .Values.image.repository }}:{{ required "image.tag hoặc image.digest là bắt buộc" .Values.image.tag }}
{{- end -}}
{{- end -}}

{{/* Pod template dùng chung cho Deployment và Rollout */}}
{{- define "ms.podTemplate" -}}
metadata:
  labels:
    {{- include "ms.labels" . | nindent 4 }}
  annotations:
    {{- if .Values.otel.javaAutoInstrumentation }}
    instrumentation.opentelemetry.io/inject-java: "ecommerce/java"
    {{- end }}
spec:
  serviceAccountName: {{ include "ms.name" . }}
  securityContext:
    runAsNonRoot: true
    runAsUser: 65532
    fsGroup: 65532
    seccompProfile: {type: RuntimeDefault}
  {{- if .Values.topologySpread }}
  topologySpreadConstraints:
    - maxSkew: 1
      topologyKey: topology.kubernetes.io/zone
      whenUnsatisfiable: ScheduleAnyway
      labelSelector:
        matchLabels:
          {{- include "ms.selector" . | nindent 10 }}
  {{- end }}
  {{- if .Values.migrations.enabled }}
  # Migration chạy trước app, cùng image và biến môi trường; pod chỉ nhận traffic khi schema đã sẵn sàng.
  # Nhiều pod khởi động cùng lúc vẫn an toàn: migrator giữ advisory lock của Postgres.
  initContainers:
    - name: migrate
      image: {{ include "ms.image" . }}
      imagePullPolicy: {{ .Values.image.pullPolicy }}
      command: {{- toYaml .Values.migrations.command | nindent 8 }}
      env:
        - {name: NODE_ENV, value: production}
        {{- range $k, $v := .Values.env }}
        - {name: {{ $k }}, value: {{ $v | quote }}}
        {{- end }}
      {{- if .Values.secretEnv }}
      envFrom:
        - secretRef: {name: {{ include "ms.name" . }}}
      {{- end }}
      resources:
        requests: {cpu: 50m, memory: 128Mi}
        limits: {memory: 256Mi}
      securityContext:
        allowPrivilegeEscalation: false
        readOnlyRootFilesystem: true
        capabilities: {drop: [ALL]}
  {{- end }}
  containers:
    - name: app
      image: {{ include "ms.image" . }}
      imagePullPolicy: {{ .Values.image.pullPolicy }}
      {{- with .Values.args }}
      args: {{- toYaml . | nindent 8 }}
      {{- end }}
      ports:
        - {name: http, containerPort: {{ .Values.ports.http }}}
        {{- if .Values.ports.grpc }}
        - {name: grpc, containerPort: {{ .Values.ports.grpc }}}
        {{- end }}
      env:
        - {name: SERVICE_VERSION, value: {{ .Values.image.tag | default "unknown" | quote }}}
        - {name: OTEL_EXPORTER_OTLP_ENDPOINT, value: {{ .Values.otel.endpoint | quote }}}
        - {name: OTEL_SERVICE_NAME, value: {{ include "ms.name" . | quote }}}
        - name: OTEL_RESOURCE_ATTRIBUTES
          value: "k8s.namespace.name=$(POD_NAMESPACE),k8s.pod.name=$(POD_NAME)"
        - name: POD_NAME
          valueFrom: {fieldRef: {fieldPath: metadata.name}}
        - name: POD_NAMESPACE
          valueFrom: {fieldRef: {fieldPath: metadata.namespace}}
        {{- if eq .Values.runtime "go" }}
        - {name: HTTP_ADDR, value: ":{{ .Values.ports.http }}"}
        {{- if .Values.ports.grpc }}
        - {name: GRPC_ADDR, value: ":{{ .Values.ports.grpc }}"}
        {{- end }}
        - name: GOMEMLIMIT
          valueFrom: {resourceFieldRef: {resource: limits.memory, divisor: "1"}}
        {{- end }}
        {{- if eq .Values.runtime "node" }}
        - {name: HTTP_PORT, value: {{ .Values.ports.http | quote }}}
        {{- if .Values.ports.grpc }}
        - {name: GRPC_PORT, value: {{ .Values.ports.grpc | quote }}}
        {{- end }}
        - {name: NODE_ENV, value: production}
        - {name: LOG_FORMAT, value: json}
        {{- end }}
        {{- if eq .Values.runtime "python" }}
        - {name: HTTP_PORT, value: {{ .Values.ports.http | quote }}}
        - {name: LOG_FORMAT, value: json}
        {{- end }}
        {{- if eq .Values.runtime "java" }}
        - {name: SERVER_PORT, value: {{ .Values.ports.http | quote }}}
        - {name: JAVA_TOOL_OPTIONS, value: "-XX:MaxRAMPercentage=75 -XX:+UseZGC"}
        {{- end }}
        {{- if eq .Values.runtime "java-native" }}
        - {name: SERVER_PORT, value: {{ .Values.ports.http | quote }}}
        {{- end }}
        {{- range $k, $v := .Values.env }}
        - {name: {{ $k }}, value: {{ $v | quote }}}
        {{- end }}
      {{- if .Values.secretEnv }}
      envFrom:
        - secretRef: {name: {{ include "ms.name" . }}}
      {{- end }}
      readinessProbe:
        httpGet: {path: {{ .Values.probes.readinessPath }}, port: http}
        periodSeconds: 5
        failureThreshold: 3
      livenessProbe:
        httpGet: {path: {{ .Values.probes.livenessPath }}, port: http}
        initialDelaySeconds: {{ if eq .Values.runtime "java" }}60{{ else }}5{{ end }}
        periodSeconds: 10
      startupProbe:
        httpGet: {path: {{ .Values.probes.livenessPath }}, port: http}
        periodSeconds: 5
        failureThreshold: {{ if eq .Values.runtime "java" }}36{{ else }}6{{ end }}
      resources:
        {{- toYaml .Values.resources | nindent 8 }}
      securityContext:
        allowPrivilegeEscalation: false
        readOnlyRootFilesystem: true
        capabilities: {drop: [ALL]}
      volumeMounts:
        - {name: tmp, mountPath: /tmp}
  volumes:
    - {name: tmp, emptyDir: {}}
  {{- with .Values.nodeSelector }}
  nodeSelector: {{- toYaml . | nindent 4 }}
  {{- end }}
  {{- with .Values.tolerations }}
  tolerations: {{- toYaml . | nindent 4 }}
  {{- end }}
{{- end -}}
