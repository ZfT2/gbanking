package de.zft2.gbanking.db.dao;

public class MnDao extends Dao {

	protected int mTableId;
	protected int nTableId;

	public MnDao() {
		super();
	}

	public MnDao(int mTableId, int nTableId) {
		super();
		this.mTableId = mTableId;
		this.nTableId = nTableId;
	}

	public int getmTableId() {
		return mTableId;
	}

	public void setmTableId(int mTableId) {
		this.mTableId = mTableId;
	}

	public int getnTableId() {
		return nTableId;
	}

	public void setnTableId(int nTableId) {
		this.nTableId = nTableId;
	}

}
