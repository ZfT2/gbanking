package de.zft2.gbanking.db.dao;

import java.time.LocalDate;

import de.zft2.gbanking.db.dao.enu.Source;

public abstract class Dao {

	protected boolean selected;
	protected int id;
	protected Source source;
	protected LocalDate updatedAt;

	public boolean isSelected() {
		return selected;
	}

	public void setSelected(boolean selected) {
		this.selected = selected;
	}

	public int getId() {
		return id;
	}

	public void setId(int id) {
		this.id = id;
	}

	public Source getSource() {
		return source;
	}

	public void setSource(Source source) {
		this.source = source;
	}

	public LocalDate getUpdatedAt() {
		return updatedAt;
	}

	public void setUpdatedAt(LocalDate updatedAt) {
		this.updatedAt = updatedAt;
	}

}
