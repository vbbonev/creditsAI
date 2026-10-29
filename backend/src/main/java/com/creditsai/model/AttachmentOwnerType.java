package com.creditsai.model;

import lombok.Getter;

/**
 * The kinds of records that can own attachments. It names the storage folder and the word used in
 * messages, so the shared upload, storage and cleanup code needs no {@code if/else} on the kind.
 */
@Getter
public enum AttachmentOwnerType {

    /** Files of a client, stored under {@code clients/{clientId}}. */
    CLIENT("clients", "client"),

    /** Files of a credit, stored under {@code credits/{creditId}}. */
    CREDIT("credits", "credit");

    /** The folder below the storage directory that holds the folders of this kind of owner. */
    private final String directoryName;

    /** The singular word for messages, e.g. "client". */
    private final String label;

    AttachmentOwnerType(String directoryName, String label) {
        this.directoryName = directoryName;
        this.label = label;
    }
}
