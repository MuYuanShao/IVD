package com.zlth.ivd.common.event;
/** Synchronous, before-commit event. A billing failure must roll back the source stock mutation. */
public record StockBusinessRecordedEvent(Long recordId,String recordType) {}
