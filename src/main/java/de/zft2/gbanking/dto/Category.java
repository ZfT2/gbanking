package de.zft2.gbanking.dto;

import java.util.Objects;

public class Category {

	private Integer id;
	private Integer parentId;
	private String categoryFullName;

	public Integer getId() {
		return id;
	}

	public void setId(Integer id) {
		this.id = id;
	}

	public Integer getParentId() {
		return parentId;
	}

	public void setParentId(Integer parentId) {
		this.parentId = parentId;
	}

	public String getCategoryFullName() {
		return categoryFullName;
	}

	public void setCategoryFullName(String categoryFullName) {
		this.categoryFullName = categoryFullName;
	}

	@Override
	public int hashCode() {
		return Objects.hash(categoryFullName, id, parentId);
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
		return Objects.equals(categoryFullName, other.categoryFullName) && Objects.equals(id, other.id)
				&& Objects.equals(parentId, other.parentId);
	}

}
