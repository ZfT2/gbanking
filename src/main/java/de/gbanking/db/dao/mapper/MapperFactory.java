package de.gbanking.db.dao.mapper;

public class MapperFactory {

	private MapperFactory() {
	}

//	public static <T> AbstractDaoMapper<? extends Dao, Void> getMapper(T dao) {
//
//		AbstractDaoMapper<? extends Dao, Void> mapper = null;
//
//		switch (dao) {
//		case BankAccess bA:
//			mapper = new BankAccessMapper();
//			break;
//		case BankAccount bC:
//			mapper = new BankAccountMapper();
//			break;
//		case Booking bk:
//			mapper = new BookingMapper();
//			break;
//		case MoneyTransfer mt:
//			mapper = new MoneytransferMapper();
//			break;
//		case Recipient rp:
//			mapper = new RecipientMapper();
//			break;
//		case Category cg:
//			mapper = new CategoryMapper();
//			break;
//		case CategoryRule cr:
//			mapper = new CategoryRuleMapper();
//			break;
////		case MnDao mn:
////			mapper = new MtoNTableMapper();
////			break;
//		default:
//			throw new GBankingException("No Mapper avaliable for:  " + dao.getClass());
//		}
//		return mapper;
//	}

}
