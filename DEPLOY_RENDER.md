# Render deployment instructions for Spring Boot + PostgreSQL

## 1. Crear servicio web en Render

1. Ve a https://dashboard.render.com y crea un nuevo servicio de tipo "Web Service".
2. Conecta tu repositorio de GitHub o sube el código manualmente.
3. Elige el Dockerfile detectado automáticamente (ya está listo en tu proyecto).

## 2. Añadir base de datos PostgreSQL

1. En Render, crea un nuevo recurso de tipo "PostgreSQL".
2. Copia las credenciales (host, user, password, database, port).

## 3. Variables de entorno

En la sección "Environment" de tu servicio web, agrega:

- `SPRING_DATASOURCE_URL=jdbc:postgresql://<host>:<port>/<database>`
- `SPRING_DATASOURCE_USERNAME=<user>`
- `SPRING_DATASOURCE_PASSWORD=<password>`
- `SPRING_JPA_HIBERNATE_DDL_AUTO=update`
- `SPRING_PROFILES_ACTIVE=prod`

Agrega también las variables necesarias para Auth0 y cualquier otro secreto.

## 4. Script de seed para categorías globales

Sube y ejecuta el archivo `seed_categories.sql` en tu base de datos de Render (puedes usar el panel de SQL de Render o un cliente externo como DBeaver o TablePlus).

## 5. Deploy

- Render detectará el Dockerfile y construirá la imagen automáticamente.
- El servicio se expondrá en el puerto 8080 (puedes cambiarlo si lo necesitas).
- Accede a la URL pública que Render te proporciona.

---

**¡Listo! Tu app estará disponible y las categorías globales estarán presentes si ejecutas el seed.**
