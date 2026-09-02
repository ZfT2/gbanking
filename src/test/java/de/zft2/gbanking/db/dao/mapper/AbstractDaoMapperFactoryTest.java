package de.zft2.gbanking.db.dao.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import de.zft2.gbanking.db.SqlFields;
import de.zft2.gbanking.db.StatementsConfig.ResultType;
import de.zft2.gbanking.db.dao.Category;
import de.zft2.gbanking.db.dao.Dao;

class AbstractDaoMapperFactoryTest {

	@Test
	void statementsResultMapperShouldCreateDaoWithoutDefaultConstructor() throws SQLException {
		assertFalse(Arrays.stream(Category.class.getDeclaredConstructors()).anyMatch(constructor -> constructor.getParameterCount() == 0));

		ResultSet resultSet = mock(ResultSet.class);
		when(resultSet.getInt("id")).thenReturn(17);
		when(resultSet.getDate(SqlFields.DAO_UPDATEDAT)).thenReturn(Date.valueOf(LocalDate.of(2026, 7, 27)));
		when(resultSet.getString("name")).thenReturn("Miete");
		when(resultSet.getString("fullName")).thenReturn("Wohnen:Miete");

		Category category = (Category) StatementsResultMapper.toDao(Category.class, resultSet, ResultType.FULL);

		assertEquals(17, category.getId());
		assertEquals("Miete", category.getName());
		assertEquals("Wohnen:Miete", category.getFullName());
		assertEquals(LocalDate.of(2026, 7, 27), category.getUpdatedAt());
	}

	@Test
	void initResultDaoShouldUseRegisteredFactory() throws SQLException {
		AtomicInteger factoryCalls = new AtomicInteger();
		FactoryOnlyMapper mapper = new FactoryOnlyMapper(factoryCalls);
		ResultSet resultSet = mock(ResultSet.class);
		when(resultSet.getInt("id")).thenReturn(23);

		FactoryOnlyDao result = mapper.initResultDao(resultSet);

		assertEquals(1, factoryCalls.get());
		assertEquals("factory", result.marker());
		assertEquals(23, result.getId());
	}

	@Test
	void batchMappingShouldReuseSelectedMapper() throws SQLException {
		AtomicInteger factoryCalls = new AtomicInteger();
		FactoryOnlyMapper mapper = new FactoryOnlyMapper(factoryCalls);
		PreparedStatement statement = mock(PreparedStatement.class);
		LinkedHashSet<FactoryOnlyDao> values = new LinkedHashSet<>(
				List.of(new FactoryOnlyDao("first"), new FactoryOnlyDao("second")));

		mapper.setParamsFull(values, statement);

		assertEquals(List.of("first", "second"), mapper.mappedMarkers());
		assertEquals(0, factoryCalls.get());
		verify(statement, times(2)).addBatch();
	}

	private static final class FactoryOnlyDao extends Dao {

		private final String marker;

		private FactoryOnlyDao(String marker) {
			this.marker = marker;
		}

		private String marker() {
			return marker;
		}
	}

	private static final class FactoryOnlyMapper extends AbstractDaoMapper<FactoryOnlyDao, Void> {

		private final List<String> mappedMarkers = new ArrayList<>();

		private FactoryOnlyMapper(AtomicInteger factoryCalls) {
			super(() -> {
				factoryCalls.incrementAndGet();
				return new FactoryOnlyDao("factory");
			});
		}

		@Override
		public void setParamsFull(FactoryOnlyDao dao, PreparedStatement statement) throws SQLException {
			mappedMarkers.add(dao.marker());
			statement.setString(1, dao.marker());
		}

		@Override
		void mapDao(FactoryOnlyDao dao, ResultType resultType, ResultSet resultSet) {
			// No additional fields are required for this focused factory test.
		}

		private List<String> mappedMarkers() {
			return List.copyOf(mappedMarkers);
		}
	}
}
