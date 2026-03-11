package de.gbanking.db.dao.logic;

import java.util.List;

import de.gbanking.db.dao.Dao;

public class MnDao extends Dao {
	private final int mainId;
	private final List<Integer> idList;

	public MnDao(Integer x, List<Integer> z) {
		this.mainId = x;
		this.idList = z;
	}

	public int getMainId() {
		return mainId;
	}

	public List<Integer> getIdList() {
		return idList;
	}
}
