package de.zft2.gbanking.db.dao;

import java.io.Serializable;

import de.zft2.gbanking.db.dao.enu.DataType;

public class Setting extends Dao implements Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = -413211466146017773L;

	private String attribute;
	private String value;
	private DataType dataType;
	private boolean editable;
	private boolean visible;
	private String comment;

	public String getAttribute() {
		return attribute;
	}

	public void setAttribute(String attribute) {
		this.attribute = attribute;
	}

	public String getValue() {
		return value;
	}

	public void setValue(String value) {
		this.value = value;
	}

	public DataType getDataType() {
		return dataType;
	}

	public void setDataType(DataType dataType) {
		this.dataType = dataType;
	}

	public boolean isEditable() {
		return editable;
	}

	public void setEditable(boolean editable) {
		this.editable = editable;
	}

	public boolean isVisible() {
		return visible;
	}

	public void setVisible(boolean visible) {
		this.visible = visible;
	}

	public String getComment() {
		return comment;
	}

	public void setComment(String comment) {
		this.comment = comment;
	}
}
