# BJJ Rotation — projeto Android

Projeto-base Android Studio (Kotlin) para o fluxo de rotação temporal de vídeos.

## Abrir e compilar
1. Instale Android Studio atualizado (com Android SDK 35).
2. Abra a pasta `BJJ-Rotation` no Android Studio.
3. Aguarde a sincronização do Gradle.
4. Conecte um Android e use **Run**, ou escolha **Build > Build APK(s)**.

O APK de depuração, quando compilado pelo Android Studio, fica em `app/build/outputs/apk/debug/app-debug.apk`.

## O que está implementado neste protótipo
- Seleção de vários vídeos pela galeria/armazenamento, na ordem retornada pelo seletor.
- Tela escura e simples, com player e dial circular de rotação.
- Contagem regressiva de 3 segundos e botão para reiniciar o vídeo.
- Registro de pontos de rotação associados ao tempo de reprodução.
- Fluxo sequencial por vídeo.
- Pipeline de exportação Media3 Transformer que aplica a rotação temporal interpolada e inclui a versão espelhada logo após cada vídeo.
- Prévia do arquivo final e gravação em `Movies/BJJ Rotation`.

## Observações importantes
- Este é um primeiro projeto-fonte, não um APK já compilado. O ambiente em que foi gerado não contém Android SDK/Gradle para produzir e testar um APK aqui.
- A API Media3 Transformer evolui entre versões; se o Android Studio apontar incompatibilidade de API na sincronização/compilação, será necessário ajustar a versão do Media3 ou a assinatura indicada pelo IDE.
- A suavidade real depende do aparelho, codec, resolução, taxa de quadros e armazenamento. O preview usa ExoPlayer/TextureView e a exportação usa Media3 Transformer, mas não é honesto garantir ausência absoluta de travamentos sem testar os vídeos reais no aparelho.
- O primeiro protótipo grava amostras de rotação durante a interação; em vídeos muito longos ou de alta resolução, teste primeiro com clipes curtos. O recorte/ajuste de enquadramento após rotações grandes ainda pode precisar de refinamento.
