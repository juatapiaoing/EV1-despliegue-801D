## Qué incluye

<!-- Resumen del cambio en dos o tres frases. Enlazar el issue o la tarea si existe. -->

## Por qué

<!-- Motivación: qué problema resuelve o qué funcionalidad habilita. -->

## Cómo probarlo

<!-- Comandos, endpoints o pasos para verificar el cambio. -->

```bash
./mvnw clean verify
```

## Lista de verificación

- [ ] La rama sigue el naming `feature/*`, `hotfix/*` o `release/*` y sale de la rama correcta
- [ ] Los commits siguen Conventional Commits (`feat`, `fix`, `docs`, `ci`, `chore`, `test`, `refactor`)
- [ ] Hay pruebas para el comportamiento nuevo o corregido y toda la suite pasa en local
- [ ] El README y la documentación quedan coherentes con el cambio (API, configuración, despliegue)
- [ ] No se versionan secretos, llaves ni archivos generados (`target/`, `.env`, `*.pem`)
- [ ] La CI está en verde antes de solicitar el merge
