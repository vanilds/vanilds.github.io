package io.github.vanilds.creditdesk.proposal.document;

import io.github.vanilds.creditdesk.shared.security.Actor;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/proposals/{id}/documents")
@Tag(name = "Documents", description = "Files attached to a proposal, stored in S3 through presigned URLs")
public class DocumentController {

    private final DocumentService service;

    public DocumentController(DocumentService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ANALYST', 'AUDITOR')")
    @Operation(operationId = "listProposalDocuments", summary = "Documents of a proposal",
            description = "Only confirmed (AVAILABLE) documents, oldest first.")
    public List<DocumentResponse> list(@PathVariable UUID id) {
        return service.list(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ANALYST')")
    @Operation(operationId = "addProposalDocument", summary = "Start an upload",
            description = "Owner only, while DRAFT. PDF, JPEG or PNG up to 10 MB, at most 10 per proposal. Returns a "
                    + "presigned PUT URL valid for 5 minutes: send the file there, then call confirm.")
    public UploadResponse add(@PathVariable UUID id, @Valid @RequestBody AddDocumentRequest request, Actor actor) {
        return service.add(id, request, actor);
    }

    @PostMapping("/{documentId}/confirm")
    @PreAuthorize("hasRole('ANALYST')")
    @Operation(operationId = "confirmProposalDocument", summary = "Finish an upload",
            description = "The API checks the object in S3 (exists, same size and type) and makes it AVAILABLE. "
                    + "409 if the file was not sent yet; 422 if it differs from what was declared.")
    public DocumentResponse confirm(@PathVariable UUID id, @PathVariable UUID documentId, Actor actor) {
        return service.confirm(id, documentId, actor);
    }

    @PostMapping("/{documentId}/download")
    @PreAuthorize("hasAnyRole('ANALYST', 'AUDITOR')")
    @Operation(operationId = "downloadProposalDocument", summary = "Get a download link",
            description = "A presigned GET URL valid for about a minute. POST, not GET: every download is audited, "
                    + "so asking for a link is an action, not a harmless read.")
    public DownloadResponse download(@PathVariable UUID id, @PathVariable UUID documentId, Actor actor) {
        return service.download(id, documentId, actor);
    }
}
