package io.github.vanilds.creditdesk.proposal.document;

import io.github.vanilds.creditdesk.audit.AuditAction;
import io.github.vanilds.creditdesk.audit.AuditService;
import io.github.vanilds.creditdesk.audit.Changes;
import io.github.vanilds.creditdesk.proposal.Proposal;
import io.github.vanilds.creditdesk.proposal.ProposalService;
import io.github.vanilds.creditdesk.shared.security.Actor;
import io.github.vanilds.creditdesk.shared.web.ApiException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Upload in two steps, so a file never passes through the API:
 * <ol>
 *   <li>{@link #add}: checks the rules, stores the metadata as PENDING_UPLOAD and returns a presigned PUT URL.</li>
 *   <li>The browser sends the file straight to S3.</li>
 *   <li>{@link #confirm}: HEAD on the object — it must exist with the declared size and type — then AVAILABLE.</li>
 * </ol>
 * Only AVAILABLE documents are listed or downloadable. An upload that never got confirmed leaves an orphan object;
 * a lifecycle rule on the bucket can expire those (Phase 7).
 */
@Service
@Transactional(readOnly = true)
public class DocumentService {

    static final long MAX_SIZE = 10L * 1024 * 1024;
    static final int MAX_PER_PROPOSAL = 10;
    /** No HTML, SVG or Office files: they can carry scripts or macros, and none is needed to back a proposal. */
    static final Set<String> ALLOWED_TYPES = Set.of("application/pdf", "image/jpeg", "image/png");
    private static final String AUDIT_ENTITY = "PROPOSAL";

    private final ProposalDocumentRepository documents;
    private final ProposalService proposals;
    private final DocumentStorage storage;
    private final AuditService audit;
    private final Clock clock;

    public DocumentService(ProposalDocumentRepository documents, ProposalService proposals, DocumentStorage storage,
                           AuditService audit, Clock clock) {
        this.documents = documents;
        this.proposals = proposals;
        this.storage = storage;
        this.audit = audit;
        this.clock = clock;
    }

    public List<DocumentResponse> list(UUID proposalId) {
        proposals.require(proposalId);
        return documents.findByProposalIdAndStatusOrderByCreatedAt(proposalId, DocumentStatus.AVAILABLE).stream()
                .map(DocumentResponse::from).toList();
    }

    @Transactional
    public UploadResponse add(UUID proposalId, AddDocumentRequest request, Actor actor) {
        Proposal proposal = proposals.require(proposalId);
        proposal.requireCanAttachDocuments(actor);
        String contentType = request.contentType().trim().toLowerCase(Locale.ROOT);
        if (!ALLOWED_TYPES.contains(contentType)) {
            throw ApiException.badRequest("unsupported-file-type", "File type not accepted",
                    "Accepted types: PDF, JPEG or PNG; got %s.".formatted(contentType));
        }
        if (request.sizeBytes() > MAX_SIZE) {
            throw ApiException.badRequest("file-too-large", "File too large", "The limit is 10 MB per file.");
        }
        if (documents.countByProposalId(proposalId) >= MAX_PER_PROPOSAL) {
            throw ApiException.unprocessable("too-many-documents", "Too many documents",
                    "A proposal can have at most %d documents.".formatted(MAX_PER_PROPOSAL));
        }
        var document = new ProposalDocument(proposalId, request.category(), cleanFileName(request.fileName()),
                contentType, request.sizeBytes(), actor, clock.instant());
        documents.save(document);
        var upload = storage.presignUpload(document.getStorageKey(), contentType, request.sizeBytes());
        return new UploadResponse(DocumentResponse.from(document), upload);
    }

    @Transactional
    public DocumentResponse confirm(UUID proposalId, UUID documentId, Actor actor) {
        Proposal proposal = proposals.require(proposalId);
        ProposalDocument document = find(proposalId, documentId);
        if (document.getStatus() == DocumentStatus.AVAILABLE) {
            return DocumentResponse.from(document); // confirming twice (a retried request) is harmless
        }
        proposal.requireCanAttachDocuments(actor);
        var stored = storage.find(document.getStorageKey()).orElseThrow(() -> ApiException.conflict(
                "upload-not-found", "File not uploaded yet", "Send the file to the upload URL first, then confirm."));
        String storedType = stored.contentType() == null ? "" : stored.contentType().toLowerCase(Locale.ROOT);
        if (stored.size() != document.getSizeBytes() || !storedType.startsWith(document.getContentType())) {
            throw ApiException.unprocessable("upload-mismatch", "Uploaded file differs",
                    "Expected %d bytes of %s, found %d bytes of %s.".formatted(document.getSizeBytes(),
                            document.getContentType(), stored.size(), stored.contentType()));
        }
        document.markAvailable(clock.instant());
        documents.saveAndFlush(document);
        audit.record(actor, AuditAction.PROPOSAL_DOCUMENT_ADDED, AUDIT_ENTITY, proposalId, new Changes()
                .created("documentId", document.getId())
                .created("category", document.getCategory())
                .created("sizeBytes", document.getSizeBytes()), null);
        return DocumentResponse.from(document);
    }

    /** Not read-only: every download is audited (who opened which file of whose proposal). */
    @Transactional
    public DownloadResponse download(UUID proposalId, UUID documentId, Actor actor) {
        proposals.require(proposalId);
        ProposalDocument document = find(proposalId, documentId);
        if (document.getStatus() != DocumentStatus.AVAILABLE) {
            throw ApiException.notFound("Document", documentId);
        }
        var signed = storage.presignDownload(document.getStorageKey(), document.getFileName());
        audit.record(actor, AuditAction.PROPOSAL_DOCUMENT_DOWNLOADED, AUDIT_ENTITY, proposalId, new Changes()
                .created("documentId", document.getId())
                .created("category", document.getCategory()), null);
        return new DownloadResponse(signed.url(), signed.expiresAt());
    }

    private ProposalDocument find(UUID proposalId, UUID documentId) {
        return documents.findByIdAndProposalId(documentId, proposalId)
                .orElseThrow(() -> ApiException.notFound("Document", documentId));
    }

    /** Keeps only the last path segment and drops control characters: "C:\\x\\..\\a.pdf" → "a.pdf". */
    static String cleanFileName(String raw) {
        String name = raw.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1).replaceAll("\\p{Cntrl}", "").trim();
        if (name.isEmpty() || name.equals(".") || name.equals("..")) {
            throw ApiException.badRequest("invalid-file-name", "Invalid file name", "The file needs a name.");
        }
        return name;
    }
}
