# PatologiaFácil (Android, Kotlin)

Aplicativo nativo para análise **on-device** de fissuras em paredes, com base em heurísticas morfológicas e regras mapeadas ao TCC. Tudo roda localmente (sem rede).

## Requisitos
- Android Studio (Koala+)
- SDK 34
- Emulador API 34 ou dispositivo físico (minSdk 24)

## Como abrir/rodar
1. Abra a pasta `PatologiaFacil/` no Android Studio.
2. Sincronize o Gradle.
3. Rode **app** em um dispositivo/emulador.
4. **Smoke test**:
   - Onboarding ➜ **Entrar**;
   - Tutorial ➜ **Começar a Usar**;
   - Abas: **Início, Capturar, Diagnóstico, Histórico**;
   - Em **Capturar**: tire foto (CameraX) ou escolha da galeria; então toque **Analisar**;
   - Em **Diagnóstico**: ver imagem, caracterização, gravidade, causas e ações;
   - **Salvar no Histórico** ➜ item aparece e persiste (Room).

## Notas
- Pipeline 100% Kotlin/SDK: grayscale → equalização local → mediana → Sobel+NMS → dupla limiarização+histérese → abertura/fechamento → afinamento (Zhang–Suen) → componentes conectados → recursos → regras para causas/gravidade.
- Persistência: Room salva `AnalysisEntity` com `imageUri`, `local`, `tempoEvento`, `resultJson`.
- Privacidade: tudo local; resultado orientativo (não substitui laudo).

## Licenças
- Ícones Material simplificados (vector drawables).
- Projeto educacional/demonstrativo.

## Configura��o da API OpenAI
- Copie o arquivo `openai.properties.example` para `openai.properties` (mantendo fora do controle de vers�o) e informe sua chave em `openai.apiKey=...`.
- Alternativamente, defina a vari�vel de ambiente `OPENAI_API_KEY`.
- O app l� a chave durante o build (BuildConfig.OPENAI_API_KEY) e repassa ao cliente de vis�o.
- A an�lise remota usa o modelo `gpt-4o-mini` para processar a imagem selecionada.
