FROM google/cloud-sdk:slim
RUN apt-get update && apt-get install -y --no-install-recommends postgresql-client cron \
    && rm -rf /var/lib/apt/lists/*
COPY backup.sh /usr/local/bin/backup.sh
RUN chmod +x /usr/local/bin/backup.sh \
    && printf 'SHELL=/bin/bash\n0 * * * * root . /etc/backup.env && /usr/local/bin/backup.sh >> /var/log/backup.log 2>&1\n' > /etc/cron.d/ember-backup \
    && chmod 0644 /etc/cron.d/ember-backup
# cron jobs do not inherit the container env: dump it where the cron line sources it
CMD ["bash", "-c", "for v in PGHOST PGUSER PGPASSWORD PGDATABASE GCS_BUCKET HOURLY_RETENTION WEEKLY_RETENTION; do printf 'export %s=%q\\n' \"$v\" \"${!v}\"; done > /etc/backup.env && chmod 600 /etc/backup.env && cron && tail -F /var/log/backup.log"]
