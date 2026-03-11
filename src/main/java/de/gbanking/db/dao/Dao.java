package de.gbanking.db.dao;

import java.util.Calendar;

import de.gbanking.db.dao.enu.Source;

public abstract class Dao {

	protected boolean selected;
	protected int id;
	protected Source source;
	protected Calendar updatedAt;

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

	public Calendar getUpdatedAt() {
		return updatedAt;
	}

	public void setUpdatedAt(Calendar updatedAt) {
		this.updatedAt = updatedAt;
	}

}
