package de.gbanking;

public class CategoryParam {

	private String paramField;
	private String operator;
	private Object value;

	public CategoryParam(String paramField, String operator, Object value) {
		super();
		this.paramField = paramField;
		this.operator = operator;
		this.value = value;
	}

	public String getParamField() {
		return paramField;
	}

	public String getOperator() {
		return operator;
	}

	public Object getValue() {
		return value;
	}

}
