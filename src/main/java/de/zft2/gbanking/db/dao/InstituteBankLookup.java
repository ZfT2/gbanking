package de.zft2.gbanking.db.dao;

/** Read-only bank lookup from the source-prioritized institute view. */
public record InstituteBankLookup(String blz, String bic, String bankName, int importNumber, int sourcePriority) {
}
