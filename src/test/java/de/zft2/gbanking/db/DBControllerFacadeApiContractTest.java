package de.zft2.gbanking.db;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import de.zft2.gbanking.db.StatementsConfig.ResultType;
import de.zft2.gbanking.db.StatementsConfig.StatementType;
import de.zft2.gbanking.db.dao.Dao;
import de.zft2.gbanking.db.enu.StateType;

class DBControllerFacadeApiContractTest {

	private static final Set<String> EXPECTED_METHODS = Set.of(
			"boolean delete(de.zft2.gbanking.db.dao.Dao,de.zft2.gbanking.db.StatementsConfig.StatementType)",
			"boolean insertAccountBookings(java.util.Collection)",
			"boolean insertBusinessCases(de.zft2.gbanking.db.dao.BankAccount)",
			"boolean insertOrUpdatePD(de.zft2.gbanking.db.dao.BankAccess)",
			"boolean isRecipientDeletable(de.zft2.gbanking.db.dao.Recipient)",
			"boolean isRecipientEditable(de.zft2.gbanking.db.dao.Recipient)",
			"boolean updateBookingsWithCategories(java.util.Map)",
			"boolean updateBookingsWithCategoryRule(de.zft2.gbanking.db.dao.CategoryRule,java.util.Set)",
			"boolean updateBookingsWithRecipients(java.util.Map)",
			"de.zft2.gbanking.db.dao.BankAccess getBankAccessByBlz(java.lang.String)",
			"de.zft2.gbanking.db.dao.BankAccess getBankAccessByBlzAndUserId(java.lang.String,java.lang.String)",
			"de.zft2.gbanking.db.dao.BankAccess getBankAccessById(int)",
			"de.zft2.gbanking.db.dao.BankAccountRetrievalStatus getBankAccountRetrievalStatus(int)",
			"de.zft2.gbanking.db.dao.Booking findCrossBooking(de.zft2.gbanking.db.dao.Booking)",
			"de.zft2.gbanking.db.dao.Dao find(java.lang.Class,de.zft2.gbanking.db.dao.Dao)",
			"de.zft2.gbanking.db.dao.Dao getById(java.lang.Class,int)",
			"de.zft2.gbanking.db.dao.Dao getByIdFull(java.lang.Class,int)",
			"de.zft2.gbanking.db.dao.Dao insertOrUpdate(de.zft2.gbanking.db.dao.Dao)",
			"de.zft2.gbanking.db.dao.Recipient findPreferredRecipientByIban(java.lang.String)",
			"de.zft2.gbanking.db.dao.Recipient resolveRecipient(de.zft2.gbanking.db.dao.Recipient)",
			"de.zft2.gbanking.db.dao.Recipient resolveRecipientForManualBooking(de.zft2.gbanking.db.dao.Booking,de.zft2.gbanking.db.dao.Recipient)",
			"int clearBookingCategories(java.util.Collection)",
			"int clearBookingCrossBookingIds(java.util.Collection)",
			"int executeSimpleUpdate(java.util.List,de.zft2.gbanking.db.StatementsConfig.StatementType,java.lang.Class)",
			"int updateRecipientDefault(int,boolean)",
			"java.lang.Object executeInTransaction(java.util.function.Supplier)",
			"java.lang.Object getSingleResultField(de.zft2.gbanking.db.dao.Dao,de.zft2.gbanking.db.StatementsConfig.StatementType,java.lang.Class)",
			"java.util.List getAll(java.lang.Class)",
			"java.util.List getAll(java.lang.Class,java.lang.String)",
			"java.util.List getAllBankAccountIdentifiers()",
			"java.util.List getAllByParent(java.lang.Class,java.lang.Integer)",
			"java.util.List getAllByParentFull(java.lang.Class,java.lang.Integer)",
			"java.util.List getAllByParentSpecific(de.zft2.gbanking.db.dao.Dao,java.lang.Integer,de.zft2.gbanking.db.StatementsConfig.StatementType)",
			"java.util.List getAllByParentSpecific(java.lang.Class,java.lang.Integer,de.zft2.gbanking.db.StatementsConfig.StatementType)",
			"java.util.List getAllByParentWithFilter(java.lang.Class,java.lang.Integer,de.zft2.gbanking.db.enu.StateType)",
			"java.util.List getAllFull(java.lang.Class)",
			"java.util.List getAllSpecific(java.lang.Class,de.zft2.gbanking.db.StatementsConfig.StatementType)",
			"java.util.List getAllWithFilter(java.lang.Class,de.zft2.gbanking.db.enu.StateType)",
			"java.util.List getBankAccountIdentifiers(int)",
			"java.util.List getSplitBookings(int)",
			"java.util.Map getAccountsIdsByAccountName()",
			"java.util.Map getCrossAccountsIdsByIbanOrNumber()",
			"java.util.Set insertAll(java.util.Set)",
			"static boolean hasOpenConnection()",
			"static boolean hasPendingMigrations(java.lang.String)",
			"static boolean prepareInstituteDatabase(java.nio.file.Path)",
			"static boolean resetConnectionIfIdle()",
			"static de.zft2.gbanking.db.DBController getInstance(java.lang.String)",
			"static de.zft2.gbanking.db.DBController getInstance(java.lang.String,de.zft2.gbanking.db.DbMigrationProgressListener)",
			"static de.zft2.gbanking.db.DBController getInstance(java.lang.String,de.zft2.gbanking.db.DbMigrationProgressListener,boolean)",
			"static java.sql.Connection getConnection()",
			"static void resetConnection()",
			"static void validateDatabaseIntegrity(java.nio.file.Path,boolean)",
			"void executeInTransaction(java.lang.Runnable)",
			"void printAccountsInDB()",
			"void printBookingsInDB()",
			"void replaceBankAccountIdentifiers(int,java.util.Collection)",
			"void setStatementParamsUpdateList(java.util.List,java.sql.PreparedStatement)",
			"void updateBookingAdditionalNote(de.zft2.gbanking.db.dao.Booking)",
			"void upsertBankAccountRetrievalStatus(de.zft2.gbanking.db.dao.BankAccountRetrievalStatus)");

	@Test
	void publicFacadeShouldMatchApiSnapshot() {
		Set<String> actualMethods = Arrays.stream(DBController.class.getMethods())
				.filter(DBControllerFacadeApiContractTest::isDeclaredByFacadeHierarchy)
				.map(DBControllerFacadeApiContractTest::toSignature)
				.collect(Collectors.toCollection(TreeSet::new));

		assertEquals(new TreeSet<>(EXPECTED_METHODS), actualMethods);
	}

	@Test
	void inheritedProtectedFacadeShouldRetainLegacyExtensionMethods() throws Exception {
		assertProtected("executeSelectId", String.class, Map.class);
		assertProtected("getResult", Class.class, int.class, ResultType.class);
		assertProtected("getResultList", Class.class, Integer.class, StatementType.class,
				StateType.class, String.class);
		assertProtected("getResultList", Class.class, Integer.class, StatementType.class,
				StateType.class, Dao.class, String.class);
		assertProtected("convertToTypedList", Iterable.class, Collection.class, Class.class);
		assertProtected("executeSqlUpdateStatementForeignKeyForList", String.class, Dao.class, Set.class);
	}

	private static void assertProtected(String methodName, Class<?>... parameterTypes) throws Exception {
		Method method = DbExecutor.class.getDeclaredMethod(methodName, parameterTypes);
		assertTrue(Modifier.isProtected(method.getModifiers()), method::toString);
	}

	private static boolean isDeclaredByFacadeHierarchy(Method method) {
		Class<?> currentType = DBController.class;
		while (currentType != null && currentType != Object.class) {
			if (currentType == method.getDeclaringClass()) {
				return true;
			}
			currentType = currentType.getSuperclass();
		}
		return false;
	}

	private static String toSignature(Method method) {
		String modifier = Modifier.isStatic(method.getModifiers()) ? "static " : "";
		String parameterTypes = Arrays.stream(method.getParameterTypes())
				.map(DBControllerFacadeApiContractTest::typeName)
				.collect(Collectors.joining(","));
		return modifier + typeName(method.getReturnType()) + " " + method.getName() + "(" + parameterTypes + ")";
	}

	private static String typeName(Class<?> type) {
		return type.isArray() ? typeName(type.getComponentType()) + "[]" : type.getCanonicalName();
	}
}
