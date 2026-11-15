# Gerar o APK sem instalar Android Studio no computador

Este projeto inclui o workflow `.github/workflows/android-apk.yml` para compilar o APK usando GitHub Actions.

1. Crie uma conta no GitHub, se ainda não tiver, e crie um repositório vazio chamado `BJJ-Rotation`.
2. Envie todo o conteúdo desta pasta para a raiz do repositório (incluindo a pasta oculta `.github`).
3. No repositório, abra **Actions** e selecione **Build BJJ Rotation APK**.
4. Clique em **Run workflow** e aguarde a execução terminar.
5. Abra a execução concluída. Em **Artifacts**, baixe `BJJ-Rotation-debug-apk`.
6. Extraia o ZIP baixado: dentro estará `app-debug.apk`. Envie esse APK ao celular e instale-o.

O workflow automatiza a compilação, mas não substitui testes reais no aparelho. Se a compilação falhar, consulte o log da etapa que falhou; o código poderá precisar de correções antes da instalação.
