# PatologiaFácil (Android, Kotlin)

Aplicativo nativo para análise **on-device** de fissuras em paredes, baseado em heurísticas morfológicas e regras alinhadas ao TCC. Tudo roda localmente (sem rede). Há, opcionalmente, um caminho de análise remota via API (OpenAI) para comparação.

> **Resumo:** Tire/importe uma foto, o app extrai traços da fissura, calcula medidas/atributos e aplica regras para sugerir gravidade, possíveis causas e ações recomendadas. Os resultados podem ser salvos no histórico local.

---

## Requisitos
- Android Studio (Koala ou superior)
- Android SDK 34
- Emulador API 34 ou dispositivo físico (minSdk 24)
- Gradle Wrapper do projeto (não é necessário versionar `gradle-*/`)

## Como abrir e rodar
1. Abra a pasta `PatologiaFacil/` no Android Studio.
2. Aguarde a sincronização do Gradle.
3. Selecione o módulo **app** e execute em um dispositivo/emulador.

### Smoke test (fluxo mínimo)
1. Onboarding -> **Entrar**  
2. Tutorial -> **Começar a Usar**  
3. Abas: **Início**, **Capturar**, **Diagnóstico**, **Histórico**  
4. Em **Capturar**: tire uma foto (CameraX) **ou** escolha da galeria; depois toque **Analisar**  
5. Em **Diagnóstico**: confira imagem, caracterização, gravidade, causas e ações  
6. **Salvar no Histórico** -> confirme que o item aparece e persiste (Room)

---

## Funcionalidades
- Captura de imagem (CameraX) e seleção da galeria
- Processamento **100% local** (on-device) usando algoritmos clássicos
- Diagnóstico com regras transparentes (explicáveis)
- Histórico de análises (Room), com imagem, tempo e JSON de resultado
- Modo opcional de análise remota (OpenAI) para comparação/validação

## Pipeline (on-device)
1. Conversão para escala de cinza
2. Equalização local de contraste
3. Filtro de mediana
4. Detecção de bordas (Sobel) + supressão de não-máximos (NMS)
5. Dupla limiarização + histerese
6. Operações morfológicas (abertura/fechamento)
7. Afinamento (Zhang-Suen)
8. Componentes conectados -> extração de atributos (comprimento, largura, razão, orientação, tortuosidade etc.)
9. Regras para gravidade/causas e recomendações de ação

> Observação: o objetivo é **orientativo/educacional**. O aplicativo **não substitui** laudo técnico.

---

## Estrutura (resumo)
```
PatologiaFacil/
 ├─ app/
 │   ├─ src/main/
 │   │   ├─ java/...        # Camadas UI, domínio e processamento
 │   │   ├─ res/            # Layouts, drawables, strings
 │   │   └─ AndroidManifest.xml
 │   └─ build.gradle.kts
 ├─ gradle/wrapper/         # Gradle Wrapper (properties/jar)
 ├─ settings.gradle.kts
 └─ README.md
```

---

## Solução de problemas
- **Build falha após clonar**: rode um *Sync Project with Gradle Files* e verifique a versão do SDK 34 instalada.
- **CameraX sem imagem**: conceda permissões de câmera/armazenamento e teste em dispositivo físico.
- **APK muito grande**: verifique recursos não usados e mantenha apenas o Gradle Wrapper versionado (evite `gradle-*/lib/*.jar` no repo).

---

## Roadmap
- Ajuste fino das regras e thresholds por perfil de parede/iluminação
- Exportar laudo em PDF a partir do diagnóstico
- Anotações manuais (desenhar/editar máscara)
- Comparação entre execuções (antes/depois)
- Benchmark com datasets públicos

---

## Licenças
- Ícones Material (vector drawables) simplificados.
- Projeto educacional/demonstrativo.

---
