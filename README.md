# DriveOps backend

## Deploy to Render

1. Push this repository to GitHub and create a new **Blueprint** in Render using the repository. Render reads `render.yaml` to create the Docker web service and its PostgreSQL database.
2. Wait for the first deployment to finish. The Blueprint sets the `prod` Spring profile, generates `JWT_SECRET`, connects the service to the database, and allows the deployed DriveOps Vercel frontend through CORS.
3. In the Render service settings, confirm the frontend URL in `CORS_ALLOWED_ORIGINS` matches the actual frontend origin.
4. Set the frontend's existing backend/API base URL environment variable to the Render service URL, then redeploy the frontend.
5. Check `https://<your-render-service>.onrender.com/actuator/health` to verify the backend is healthy.

The Blueprint uses Render's free web-service and PostgreSQL plans for initial deployment. Free PostgreSQL databases expire after Render's free-plan retention period; use a paid database plan for persistent production data.

Uploaded documents are stored in the web service's local filesystem at `/tmp/driveops-uploads`. Render's free web service has an ephemeral filesystem, so these uploads can be lost on restart or redeploy. Use persistent object storage or a paid Render service with a persistent disk before relying on uploaded files in production.

## Run locally

The default `dev` profile expects PostgreSQL at `localhost:5432` with database `demodata` and user `postgres`. Override these settings with `DATABASE_URL`, `DATABASE_USERNAME`, and `DATABASE_PASSWORD` as needed.
