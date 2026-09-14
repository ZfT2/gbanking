package de.zft2.gbanking.db.dao.stock;

import java.time.LocalDateTime;

import de.zft2.gbanking.db.dao.Dao;

public abstract class StockDao extends Dao {

	private LocalDateTime createdAt;
	private LocalDateTime modifiedAt;

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}

	public void setCreatedAt(LocalDateTime createdAt) {
		this.createdAt = createdAt;
	}

	public LocalDateTime getModifiedAt() {
		return modifiedAt;
	}

	public void setModifiedAt(LocalDateTime modifiedAt) {
		this.modifiedAt = modifiedAt;
	}
}
