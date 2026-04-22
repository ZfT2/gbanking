package de.zft2.gbanking.tenant;

public record TenantProfile(String id, String username, String passwordSalt, String passwordHash) {
}
