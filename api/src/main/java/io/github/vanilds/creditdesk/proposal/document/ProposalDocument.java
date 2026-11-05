package io.github.vanilds.creditdesk.proposal.document;

import io.github.vanilds.creditdesk.shared.security.Actor;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** Metadata of a file attached to a proposal. The bytes are in S3, under {@link #storageKey}. */
@Entity
@Table(name = "proposal_document")
public class ProposalDocument {

    @Id
    private UUID id;

    @Column(name = "proposal_id", nullable = false, updatable = false)
    private UUID proposalId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private DocumentCategory category;

    /** As the user named it; only shown on screen and used for the download name, never as a path. */
    @Column(name = "file_name", nullable = false, updatable = false)
    private String fileName;

    @Column(name = "content_type", nullable = false, updatable = false)
    private String contentType;

    @Column(name = "size_bytes", nullable = false, updatable = false)
    private long sizeBytes;

    /**
     * proposals/{proposalId}/{documentId}. Built only from ids: a file name can carry personal data ("payslip
     * Maria Silva.pdf") or path tricks ("../../x"), and S3 keys show up in logs and access reports.
     */
    @Column(name = "storage_key", nullable = false, updatable = false)
    private String storageKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DocumentStatus status;

    @Column(name = "uploaded_by_id", nullable = false, updatable = false)
    private String uploadedById;

    @Column(name = "uploaded_by_name", nullable = false, updatable = false)
    private String uploadedByName;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    protected ProposalDocument() {
        // for JPA
    }

    ProposalDocument(UUID proposalId, DocumentCategory category, String fileName, String contentType,
                     long sizeBytes, Actor uploader, Instant now) {
        this.id = UUID.randomUUID();
        this.proposalId = proposalId;
        this.category = category;
        this.fileName = fileName;
        this.contentType = contentType;
        this.sizeBytes = sizeBytes;
        this.storageKey = "proposals/%s/%s".formatted(proposalId, id);
        this.status = DocumentStatus.PENDING_UPLOAD;
        this.uploadedById = uploader.id();
        this.uploadedByName = uploader.username();
        this.createdAt = now;
    }

    void markAvailable(Instant now) {
        this.status = DocumentStatus.AVAILABLE;
        this.confirmedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public UUID getProposalId() {
        return proposalId;
    }

    public DocumentCategory getCategory() {
        return category;
    }

    public String getFileName() {
        return fileName;
    }

    public String getContentType() {
        return contentType;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public String getStorageKey() {
        return storageKey;
    }

    public DocumentStatus getStatus() {
        return status;
    }

    public String getUploadedById() {
        return uploadedById;
    }

    public String getUploadedByName() {
        return uploadedByName;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getConfirmedAt() {
        return confirmedAt;
    }
}
