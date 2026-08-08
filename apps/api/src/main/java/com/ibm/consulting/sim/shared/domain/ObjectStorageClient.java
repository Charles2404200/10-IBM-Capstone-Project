package com.ibm.consulting.sim.shared.domain;

/**
 * Port for durable object storage (IBM Cloud Object Storage in production).
 * Used to persist artefacts that don't belong in the relational store —
 * meeting transcript exports, uploaded scenario documents, generated reports.
 */
public interface ObjectStorageClient {

    /**
     * Uploads content under {@code key} and returns a stable reference
     * (object key or URI) that can be used later to retrieve or link to it.
     */
    String upload(String key, byte[] content, String contentType);
}
