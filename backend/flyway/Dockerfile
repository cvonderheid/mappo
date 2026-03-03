FROM flyway/flyway:10

# Bundle MAPPO migrations so the ACA job can run fully in-cluster.
COPY backend/flyway/sql /flyway/sql

ENV FLYWAY_LOCATIONS=filesystem:/flyway/sql
ENV FLYWAY_CONNECT_RETRIES=10

CMD ["migrate"]
