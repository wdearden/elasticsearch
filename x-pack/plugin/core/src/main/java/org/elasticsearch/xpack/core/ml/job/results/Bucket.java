/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License;
 * you may not use this file except in compliance with the Elastic License.
 */
package org.elasticsearch.xpack.core.ml.job.results;

import org.elasticsearch.Version;
import org.elasticsearch.common.ParseField;
import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;
import org.elasticsearch.common.io.stream.Writeable;
import org.elasticsearch.common.xcontent.ConstructingObjectParser;
import org.elasticsearch.common.xcontent.ObjectParser.ValueType;
import org.elasticsearch.common.xcontent.ToXContentObject;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentParser.Token;
import org.elasticsearch.xpack.core.ml.job.config.Job;
import org.elasticsearch.xpack.core.ml.utils.ExceptionsHelper;
import org.elasticsearch.xpack.core.ml.utils.time.TimeUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Bucket Result POJO
 */
public class Bucket implements ToXContentObject, Writeable {
    /*
     * Field Names
     */
    private static final ParseField JOB_ID = Job.ID;

    public static final ParseField ANOMALY_SCORE = new ParseField("anomaly_score");
    public static final ParseField INITIAL_ANOMALY_SCORE = new ParseField("initial_anomaly_score");
    public static final ParseField EVENT_COUNT = new ParseField("event_count");
    public static final ParseField RECORDS = new ParseField("records");
    public static final ParseField BUCKET_INFLUENCERS = new ParseField("bucket_influencers");
    public static final ParseField BUCKET_SPAN = new ParseField("bucket_span");
    public static final ParseField PROCESSING_TIME_MS = new ParseField("processing_time_ms");
    public static final ParseField PARTITION_SCORES = new ParseField("partition_scores");
    public static final ParseField SCHEDULED_EVENTS = new ParseField("scheduled_events");

    // Used for QueryPage
    public static final ParseField RESULTS_FIELD = new ParseField("buckets");

    /**
     * Result type
     */
    public static final String RESULT_TYPE_VALUE = "bucket";
    public static final ParseField RESULT_TYPE_FIELD = new ParseField(RESULT_TYPE_VALUE);

    public static final ConstructingObjectParser<Bucket, Void> STRICT_PARSER = createParser(false);
    public static final ConstructingObjectParser<Bucket, Void> LENIENT_PARSER = createParser(true);

    private static ConstructingObjectParser<Bucket, Void> createParser(boolean ignoreUnknownFields) {
        ConstructingObjectParser<Bucket, Void> parser = new ConstructingObjectParser<>(RESULT_TYPE_VALUE, ignoreUnknownFields,
                a -> new Bucket((String) a[0], (Date) a[1], (long) a[2]));

        parser.declareString(ConstructingObjectParser.constructorArg(), JOB_ID);
        parser.declareField(ConstructingObjectParser.constructorArg(), p -> {
            if (p.currentToken() == Token.VALUE_NUMBER) {
                return new Date(p.longValue());
            } else if (p.currentToken() == Token.VALUE_STRING) {
                return new Date(TimeUtils.dateStringToEpoch(p.text()));
            }
            throw new IllegalArgumentException("unexpected token [" + p.currentToken() + "] for ["
                    + Result.TIMESTAMP.getPreferredName() + "]");
        }, Result.TIMESTAMP, ValueType.VALUE);
        parser.declareLong(ConstructingObjectParser.constructorArg(), BUCKET_SPAN);
        parser.declareDouble(Bucket::setAnomalyScore, ANOMALY_SCORE);
        parser.declareDouble(Bucket::setInitialAnomalyScore, INITIAL_ANOMALY_SCORE);
        parser.declareBoolean(Bucket::setInterim, Result.IS_INTERIM);
        parser.declareLong(Bucket::setEventCount, EVENT_COUNT);
        parser.declareObjectArray(Bucket::setRecords, ignoreUnknownFields ? AnomalyRecord.LENIENT_PARSER : AnomalyRecord.STRICT_PARSER,
                RECORDS);
        parser.declareObjectArray(Bucket::setBucketInfluencers, ignoreUnknownFields ?
                BucketInfluencer.LENIENT_PARSER : BucketInfluencer.STRICT_PARSER, BUCKET_INFLUENCERS);
        parser.declareLong(Bucket::setProcessingTimeMs, PROCESSING_TIME_MS);
        parser.declareObjectArray(Bucket::setPartitionScores, ignoreUnknownFields ?
                PartitionScore.LENIENT_PARSER : PartitionScore.STRICT_PARSER, PARTITION_SCORES);
        parser.declareString((bucket, s) -> {}, Result.RESULT_TYPE);
        parser.declareStringArray(Bucket::setScheduledEvents, SCHEDULED_EVENTS);

        return parser;
    }

    private final String jobId;
    private final Date timestamp;
    private final long bucketSpan;
    private double anomalyScore;
    private double initialAnomalyScore;
    private List<AnomalyRecord> records = new ArrayList<>();
    private long eventCount;
    private boolean isInterim;
    private List<BucketInfluencer> bucketInfluencers = new ArrayList<>(); // Can't use emptyList as might be appended to
    private long processingTimeMs;
    private List<PartitionScore> partitionScores = Collections.emptyList();
    private List<String> scheduledEvents = Collections.emptyList();

    public Bucket(String jobId, Date timestamp, long bucketSpan) {
        this.jobId = jobId;
        this.timestamp = ExceptionsHelper.requireNonNull(timestamp, Result.TIMESTAMP.getPreferredName());
        this.bucketSpan = bucketSpan;
    }

    public Bucket(Bucket other) {
        this.jobId = other.jobId;
        this.timestamp = other.timestamp;
        this.bucketSpan = other.bucketSpan;
        this.anomalyScore = other.anomalyScore;
        this.initialAnomalyScore = other.initialAnomalyScore;
        this.records = new ArrayList<>(other.records);
        this.eventCount = other.eventCount;
        this.isInterim = other.isInterim;
        this.bucketInfluencers = new ArrayList<>(other.bucketInfluencers);
        this.processingTimeMs = other.processingTimeMs;
        this.partitionScores = new ArrayList<>(other.partitionScores);
        this.scheduledEvents = new ArrayList<>(other.scheduledEvents);
    }

    public Bucket(StreamInput in) throws IOException {
        jobId = in.readString();
        timestamp = new Date(in.readLong());
        anomalyScore = in.readDouble();
        bucketSpan = in.readLong();
        initialAnomalyScore = in.readDouble();
        // bwc for recordCount
        if (in.getVersion().before(Version.V_5_5_0)) {
            in.readInt();
        }
        records = in.readList(AnomalyRecord::new);
        eventCount = in.readLong();
        isInterim = in.readBoolean();
        bucketInfluencers = in.readList(BucketInfluencer::new);
        processingTimeMs = in.readLong();
        // bwc for perPartitionMaxProbability
        if (in.getVersion().before(Version.V_5_5_0)) {
            in.readGenericValue();
        }
        partitionScores = in.readList(PartitionScore::new);
        if (in.getVersion().onOrAfter(Version.V_6_2_0)) {
            scheduledEvents = in.readList(StreamInput::readString);
            if (scheduledEvents.isEmpty()) {
                scheduledEvents = Collections.emptyList();
            }
        } else {
            scheduledEvents = Collections.emptyList();
        }
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeString(jobId);
        out.writeLong(timestamp.getTime());
        out.writeDouble(anomalyScore);
        out.writeLong(bucketSpan);
        out.writeDouble(initialAnomalyScore);
        // bwc for recordCount
        if (out.getVersion().before(Version.V_5_5_0)) {
            out.writeInt(0);
        }
        out.writeList(records);
        out.writeLong(eventCount);
        out.writeBoolean(isInterim);
        out.writeList(bucketInfluencers);
        out.writeLong(processingTimeMs);
        // bwc for perPartitionMaxProbability
        if (out.getVersion().before(Version.V_5_5_0)) {
            out.writeGenericValue(Collections.emptyMap());
        }
        out.writeList(partitionScores);
        if (out.getVersion().onOrAfter(Version.V_6_2_0)) {
            out.writeStringList(scheduledEvents);
        }
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject();
        builder.field(JOB_ID.getPreferredName(), jobId);
        builder.timeField(Result.TIMESTAMP.getPreferredName(), Result.TIMESTAMP.getPreferredName() + "_string", timestamp.getTime());
        builder.field(ANOMALY_SCORE.getPreferredName(), anomalyScore);
        builder.field(BUCKET_SPAN.getPreferredName(), bucketSpan);
        builder.field(INITIAL_ANOMALY_SCORE.getPreferredName(), initialAnomalyScore);
        if (records.isEmpty() == false) {
            builder.field(RECORDS.getPreferredName(), records);
        }
        builder.field(EVENT_COUNT.getPreferredName(), eventCount);
        builder.field(Result.IS_INTERIM.getPreferredName(), isInterim);
        builder.field(BUCKET_INFLUENCERS.getPreferredName(), bucketInfluencers);
        builder.field(PROCESSING_TIME_MS.getPreferredName(), processingTimeMs);
        if (partitionScores.isEmpty() == false) {
            builder.field(PARTITION_SCORES.getPreferredName(), partitionScores);
        }
        if (scheduledEvents.isEmpty() == false) {
            builder.field(SCHEDULED_EVENTS.getPreferredName(), scheduledEvents);
        }
        builder.field(Result.RESULT_TYPE.getPreferredName(), RESULT_TYPE_VALUE);
        builder.endObject();
        return builder;
    }

    public String getJobId() {
        return jobId;
    }

    public String getId() {
        return jobId + "_bucket_" + timestamp.getTime() + "_" + bucketSpan;
    }

    /**
     * Timestamp expressed in seconds since the epoch (rather than Java's
     * convention of milliseconds).
     */
    public long getEpoch() {
        return timestamp.getTime() / 1000;
    }

    public Date getTimestamp() {
        return timestamp;
    }

    /**
     * Bucketspan expressed in seconds
     */
    public long getBucketSpan() {
        return bucketSpan;
    }

    public double getAnomalyScore() {
        return anomalyScore;
    }

    public void setAnomalyScore(double anomalyScore) {
        this.anomalyScore = anomalyScore;
    }

    public double getInitialAnomalyScore() {
        return initialAnomalyScore;
    }

    public void setInitialAnomalyScore(double initialAnomalyScore) {
        this.initialAnomalyScore = initialAnomalyScore;
    }

    /**
     * Get all the anomaly records associated with this bucket.
     * The records are not part of the bucket document. They will
     * only be present when the bucket was retrieved and expanded
     * to contain the associated records.
     *
     * @return the anomaly records for the bucket IF the bucket was expanded.
     */
    public List<AnomalyRecord> getRecords() {
        return records;
    }

    public void setRecords(List<AnomalyRecord> records) {
        this.records = Objects.requireNonNull(records);
    }

    /**
     * The number of records (events) actually processed in this bucket.
     */
    public long getEventCount() {
        return eventCount;
    }

    public void setEventCount(long value) {
        eventCount = value;
    }

    public boolean isInterim() {
        return isInterim;
    }

    public void setInterim(boolean isInterim) {
        this.isInterim = isInterim;
    }

    public long getProcessingTimeMs() {
        return processingTimeMs;
    }

    public void setProcessingTimeMs(long timeMs) {
        processingTimeMs = timeMs;
    }

    public List<BucketInfluencer> getBucketInfluencers() {
        return bucketInfluencers;
    }

    public void setBucketInfluencers(List<BucketInfluencer> bucketInfluencers) {
        this.bucketInfluencers = Objects.requireNonNull(bucketInfluencers);
    }

    public void addBucketInfluencer(BucketInfluencer bucketInfluencer) {
        bucketInfluencers.add(bucketInfluencer);
    }

    public List<PartitionScore> getPartitionScores() {
        return partitionScores;
    }

    public void setPartitionScores(List<PartitionScore> scores) {
        partitionScores = Objects.requireNonNull(scores);
    }

    public List<String> getScheduledEvents() {
        return scheduledEvents;
    }

    public void setScheduledEvents(List<String> scheduledEvents) {
        this.scheduledEvents = ExceptionsHelper.requireNonNull(scheduledEvents, SCHEDULED_EVENTS.getPreferredName());
    }

    public double partitionInitialAnomalyScore(String partitionValue) {
        Optional<PartitionScore> first = partitionScores.stream().filter(s -> partitionValue.equals(s.getPartitionFieldValue()))
                .findFirst();

        return first.isPresent() ? first.get().getInitialRecordScore() : 0.0;
    }

    public double partitionAnomalyScore(String partitionValue) {
        Optional<PartitionScore> first = partitionScores.stream().filter(s -> partitionValue.equals(s.getPartitionFieldValue()))
                .findFirst();

        return first.isPresent() ? first.get().getRecordScore() : 0.0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(jobId, timestamp, eventCount, initialAnomalyScore, anomalyScore, records,
                isInterim, bucketSpan, bucketInfluencers, partitionScores, processingTimeMs, scheduledEvents);
    }

    /**
     * Compare all the fields and embedded anomaly records (if any)
     */
    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }

        if (other instanceof Bucket == false) {
            return false;
        }

        Bucket that = (Bucket) other;

        return Objects.equals(this.jobId, that.jobId) && Objects.equals(this.timestamp, that.timestamp)
                && (this.eventCount == that.eventCount) && (this.bucketSpan == that.bucketSpan)
                && (this.anomalyScore == that.anomalyScore) && (this.initialAnomalyScore == that.initialAnomalyScore)
                && Objects.equals(this.records, that.records) && Objects.equals(this.isInterim, that.isInterim)
                && Objects.equals(this.bucketInfluencers, that.bucketInfluencers)
                && Objects.equals(this.partitionScores, that.partitionScores)
                && (this.processingTimeMs == that.processingTimeMs)
                && Objects.equals(this.scheduledEvents, that.scheduledEvents);
    }

    /**
     * This method encapsulated the logic for whether a bucket should be normalized.
     * Buckets that have a zero anomaly score themselves and no partition scores with
     * non-zero score should not be normalized as their score will not change and they
     * will just add overhead.
     *
     * @return true if the bucket should be normalized or false otherwise
     */
    public boolean isNormalizable() {
        return anomalyScore > 0.0 || partitionScores.stream().anyMatch(s -> s.getRecordScore() > 0);
    }
}
