FROM flyway/flyway:10

# Bundle the backend-owned Flyway migrations so the ACA job can run fully in-cluster.
COPY src/main/resources/db/migration /flyway/sql
COPY flyway/flyway.conf /flyway/conf/flyway.conf

ENV FLYWAY_LOCATIONS=filesystem:/flyway/sql
ENV FLYWAY_CONNECT_RETRIES=10

CMD ["migrate"]
