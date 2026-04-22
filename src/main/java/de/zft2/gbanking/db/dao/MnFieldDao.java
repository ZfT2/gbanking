package de.zft2.gbanking.db.dao;

public class MnFieldDao<T> extends MnDao {

	protected T mnField;

	public MnFieldDao() {
		super();
	}
	
	public MnFieldDao(int mTableId, int nTableId) {
		super(mTableId, nTableId);
	}

	public T getMnField() {
		return mnField;
	}

	public void setMnField(T mnField) {
		this.mnField = mnField;
	}

}
