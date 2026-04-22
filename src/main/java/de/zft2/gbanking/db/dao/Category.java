package de.zft2.gbanking.db.dao;

import java.io.Serializable;
import java.util.Objects;

public class Category extends Dao implements Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = -6032083248173538045L;

	private Integer parentId;
	private String name;

	private String fullName;
	
	public Category(int id, String categoryFullName) {
		super();
		this.id = id;
		this.fullName = categoryFullName;
	}

	public Category(String categoryName, Integer parentCategory) {
		super();
		this.name = categoryName;
		this.parentId = parentCategory;
	}

	public Category(String categoryFullName) {
		super();
		this.fullName = categoryFullName;
	}

	public Integer getParentId() {
		return parentId;
	}

	public void setParentId(Integer parentId) {
		this.parentId = parentId;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getFullName() {
		return fullName;
	}

	public void setFullName(String fullName) {
		this.fullName = fullName;
	}

	@Override
	public int hashCode() {
		return Objects.hash(fullName);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		Category other = (Category) obj;
		return Objects.equals(fullName, other.fullName);
	}

}
