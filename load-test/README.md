# Load testing the pipeline

This folder measures how much the pipeline can take, using [k6](https://k6.io). One test run sends events at a
chosen speed and reports:

- **Achieved events/s:** events sent divided by the time until every one of them was processed. If the pipeline
  keeps up, this matches the offered rate. If it falls behind, it is lower.
- **Peak lag:** the most messages that were ever waiting in Kafka.
- **Drain s:** how long the pipeline needed to catch up after sending stopped.
- **p50 / p95 / p99 ms:** how long applying one event to Ignite took, read from Prometheus for the whole run.

These numbers come from your machine, not from a data centre. k6, Kafka, Ignite, PostgreSQL and the app all share
one laptop, so treat them as relative results ("3 partitions are faster than 1") and not as production capacity.

## One-time setup

1. Install k6: `winget install k6 --source winget`. Open a new terminal and check `k6 version`.
2. Start the stack: `docker compose up -d`.
3. Close heavy programs (browsers with many tabs, games, video calls). Plug the laptop in.

## Start the app for testing

The normal limits would refuse a load test, because every visitor, including you, is limited per minute. Switch them
off for these runs only:

```
mvn spring-boot:run "-Dspring-boot.run.arguments=--pipeline.limits.events-per-minute=0 --pipeline.archive.enabled=false"
```

`--pipeline.archive.enabled=false` leaves PostgreSQL out, so the first runs measure Kafka and Ignite alone.
Keep Grafana open at http://localhost:3000/d/credit-pipeline while the test runs and watch the panels move.

## Experiment A: how fast can it go?

```
.\load-test\sweep.ps1 -Label "3-partitions"
```

This runs 500, 2,000, 5,000, 10,000 and 20,000 events per second, 100,000 events each. At low rates "achieved"
equals "offered". The rate where achieved stops growing, and "peak lag" and "drain" start to climb, is the limit.
If a run prints "did not catch up", the pipeline could not sustain that rate.

## Experiment B: what does PostgreSQL cost?

Stop the app and start it again without `--pipeline.archive.enabled=false`, keeping the limits off:

```
mvn spring-boot:run "-Dspring-boot.run.arguments=--pipeline.limits.events-per-minute=0"
```

Then run `.\load-test\sweep.ps1 -Label "with-postgres"`. Compare with Experiment A. The archive is a separate consumer
group, so Ignite's path should slow down only a little, but both now compete for the same laptop.

## Experiment C: do partitions matter?

Stop the app and start it on a new topic with a single partition and a single consumer thread:

```
mvn spring-boot:run "-Dspring-boot.run.arguments=--pipeline.limits.events-per-minute=0 --pipeline.archive.enabled=false --pipeline.topic=credit-events-p1 --pipeline.partitions=1 --pipeline.consumer-concurrency=1"
```

Then run `.\load-test\sweep.ps1 -Label "1-partition"`. Compare with the 3-partition run from Experiment A. Three
consumer threads should reach a higher rate than one, and this comparison is the evidence for the design choice.

## Collect the results

```
.\load-test\collect.ps1
```

This prints one Markdown table from every file in `results/`. Paste it into the README under a heading such as
"Load test results", and add a line with your machine (CPU, RAM) and the date. Write only what the table shows.
If 1 partition was not slower than 3, say so. An honest result is worth more than a flattering one.

## Run a single test by hand

```
k6 run -e RATE=2000 -e TOTAL=100000 -e LABEL=my-test load-test/pipeline.js
```

Settings: `RATE` (events per second to offer), `TOTAL` (events to send), `BATCH` (events per request, default 500),
`BORROWERS` (size of the borrower pool, default 5,000), `DRAIN_TIMEOUT` (seconds to wait for catching up, default 180).
Use at least 50,000 events, or the results are too short to be meaningful.

## Reading the results carefully

- Send several runs of the same setting and compare. One run can be unlucky.
- Restart the app between experiments, so each one starts from the same state.
- If the script prints "requests were refused", the rate limit was still on. Restart with the flags above.
- Throughput through the REST endpoint includes the cost of generating the events in the app. It is a fair test of the
  whole path, but it is not a pure measure of Kafka alone.
