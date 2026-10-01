# ALTHERIA Adventure — Android

Este projeto é a versão Android do motor narrativo de Altheria. Ele reutiliza o banco de conteúdo TSV da versão desktop e oferece uma interface nativa para celular.

## Build com Android Studio

1. Abra esta pasta no Android Studio.
2. Aguarde a sincronização do Gradle.
3. Use **Build > Build APK(s)**.
4. O APK de depuração será criado em `app/build/outputs/apk/debug/app-debug.apk`.

## Build pelo GitHub Actions

O repositório inclui `.github/workflows/android.yml`. Em um repositório GitHub, basta executar o workflow **Build Altheria APK**. O artefato `altheria-adventure-debug-apk` conterá o APK.

A documentação oficial do Android recomenda `assembleDebug` para gerar um APK de depuração rapidamente; builds de release precisam ser assinados com a chave do desenvolvedor.

## Sistema de itens — v1.1

- 4.000 itens cadastrados no `items.tsv`.
- O catálogo inclui 318 itens de referência do *Life in Adventure* como uma categoria separada (`Referencia_LiA`), além de conteúdo original de Altheria.
- O conteúdo original começa por recursos primordiais, seguido de minérios/metais/gemas, plantas/ingredientes, alimentos e bebidas, e depois equipamentos, utilidades, magia, fauna/monstros e itens de missão.
- `hunger` e `thirst` são campos próprios do item e podem ser usados por qualquer alimento/bebida.
- Fome e Sede são persistidas no save do jogador.

### Regras atuais de sobrevivência

- Fome e Sede variam de 0 a 100.
- O personagem começa com 85/100 em cada uma.
- Cada avanço de dia consome 8 de Fome e 12 de Sede.
- Abaixo de 25, a falta de alimento/água aumenta a fadiga.
- Abaixo de 10, a necessidade crítica causa perda de 1 HP por avanço de dia.
- Alimentos e bebidas restauram as necessidades conforme os campos do item.
- O inventário mostra os efeitos principais do item antes do uso.
