package com.feeyo.kafka.protocol;

import static com.feeyo.kafka.protocol.types.Type.BYTES;
import static com.feeyo.kafka.protocol.types.Type.NULLABLE_BYTES;
import static com.feeyo.kafka.protocol.types.Type.RECORDS;

import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicBoolean;

import com.feeyo.kafka.codec.ApiVersionsRequest;
import com.feeyo.kafka.codec.ApiVersionsResponse;
import com.feeyo.kafka.codec.FetchRequest;
import com.feeyo.kafka.codec.FetchResponse;
import com.feeyo.kafka.codec.ListOffsetRequest;
import com.feeyo.kafka.codec.ListOffsetResponse;
import com.feeyo.kafka.codec.ProduceRequest;
import com.feeyo.kafka.codec.ProduceResponse;
import com.feeyo.kafka.protocol.types.Schema;
import com.feeyo.kafka.protocol.types.Struct;
import com.feeyo.kafka.protocol.types.Type;

/**
 @see https://github.com/apache/kafka/blob/trunk/clients/src/main/java/org/apache/kafka/common/protocol/ApiKeys.java
 */
public enum ApiKeys {

	PRODUCE(0, "Produce", ProduceRequest.schemaVersions(), ProduceResponse.schemaVersions()),
	FETCH(1, "Fetch", FetchRequest.schemaVersions(), FetchResponse.schemaVersions()),
	LIST_OFFSETS(2, "ListOffsets", ListOffsetRequest.schemaVersions(), ListOffsetResponse.schemaVersions()),
	API_VERSIONS(18, "ApiVersions", ApiVersionsRequest.schemaVersions(), ApiVersionsResponse.schemaVersions());
	
	/** the permanent and immutable id of an API--this can't change ever */
    public final short id;

    /** an english description of the api--this is for debugging and can change */
    public final String name;

    /** indicates if this is a ClusterAction request used only by brokers */
    public final boolean clusterAction;
    
    /** indicates the minimum required inter broker magic required to support the API */
    public final byte minRequiredInterBrokerMagic;
    
    public final Schema[] requestSchemas;
    public final Schema[] responseSchemas;
    public final boolean requiresDelayedAllocation;
	
	ApiKeys(int id, String name, Schema[] requestSchemas, Schema[] responseSchemas) {
		this(id, name, false, requestSchemas, responseSchemas);
	}

	ApiKeys(int id, String name, boolean clusterAction, Schema[] requestSchemas, Schema[] responseSchemas) {
		this(id, name, clusterAction, (byte) 0, requestSchemas, responseSchemas);
	}

	ApiKeys(int id, String name, boolean clusterAction, byte minRequiredInterBrokerMagic, Schema[] requestSchemas,
			Schema[] responseSchemas) {
		if (id < 0)
			throw new IllegalArgumentException("id must not be negative, id: " + id);
		this.id = (short) id;
		this.name = name;
		this.clusterAction = clusterAction;
		this.minRequiredInterBrokerMagic = minRequiredInterBrokerMagic;

		if (requestSchemas.length != responseSchemas.length)
			throw new IllegalStateException(requestSchemas.length + " request versions for api " + name + " but "
					+ responseSchemas.length + " response versions.");

		for (int i = 0; i < requestSchemas.length; ++i) {
			if (requestSchemas[i] == null)
				throw new IllegalStateException("Request schema for api " + name + " for version " + i + " is null");
			if (responseSchemas[i] == null)
				throw new IllegalStateException("Response schema for api " + name + " for version " + i + " is null");
		}

		boolean requestRetainsBufferReference = false;
		for (Schema requestVersionSchema : requestSchemas) {
			if (retainsBufferReference(requestVersionSchema)) {
				requestRetainsBufferReference = true;
				break;
			}
		}
		this.requiresDelayedAllocation = requestRetainsBufferReference;
		this.requestSchemas = requestSchemas;
		this.responseSchemas = responseSchemas;
	}


	public Schema requestSchema(short version) {
		return schemaFor(requestSchemas, version);
	}

	private Schema schemaFor(Schema[] versions, short version) {
		return versions[version];
	}
	
	public Struct parseRequest(short version, ByteBuffer buffer) {
        return requestSchema(version).read(buffer);
    }
	
	private static boolean retainsBufferReference(Schema schema) {
		final AtomicBoolean hasBuffer = new AtomicBoolean(false);
		Schema.Visitor detector = new Schema.Visitor() {
			@Override
			public void visit(Type field) {
				if (field == BYTES || field == NULLABLE_BYTES || field == RECORDS)
					hasBuffer.set(true);
			}
		};
		schema.walk(detector);
		return hasBuffer.get();
	}
	
	public Schema responseSchema(short version) {
        return schemaFor(responseSchemas, version);
    }
	
	public Struct parseResponse(short version, ByteBuffer buffer) {
        return responseSchema(version).read(buffer);
    }
}