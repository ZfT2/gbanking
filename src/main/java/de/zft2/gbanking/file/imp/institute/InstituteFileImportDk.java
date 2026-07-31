package de.zft2.gbanking.file.imp.institute;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;

import de.zft2.gbanking.db.dao.Institute;
import de.zft2.gbanking.gui.BaseWorker;
import de.zft2.gbanking.util.TypeConverter;

public class InstituteFileImportDk extends InstituteFileImport {

	public static final String DEFAULT_FILENAME = "fints_institute NEU mit BIC Master.csv";

	protected InstituteFileImportDk(String basePath, String fileName, Charset charset, BaseWorker worker) {
		super(basePath, fileName, charset, worker);
	}

	protected InstituteFileImportDk(String basePath, Charset charset) {
		this(basePath, null, charset, null);
	}

	@Override
	protected CSVFormat csvFormat() {
		return CSVFormat.DEFAULT.builder().setDelimiter(';').setHeader().setSkipHeaderRecord(true).get();
	}

	@Override
	protected Comparator<Institute> groupComparator() {
		return Comparator.comparing(Institute::getImportNumber);
	}

	@Override
	protected boolean isRelevantCurrentInstitute(Institute institute) {
		return institute.getImportNumber() > 0;
	}

	@Override
	protected boolean hasImportMetadataChanged(Institute existing, Institute imported) {
		return existing.getImportNumber() != imported.getImportNumber();
	}

	@Override
	protected void prepareMatchedInstitutes(List<MatchedInstitute> matchedInstitutes) {
		List<MatchedInstitute> institutesToRemove = new ArrayList<>();
		for (MatchedInstitute matchedInstitute : matchedInstitutes) {
			if (hasShiftedImportNumberWithSameContent(matchedInstitute)) {
				collectDuplicateImportNumberMatches(matchedInstitute, matchedInstitutes, institutesToRemove);
			}
		}
		matchedInstitutes.removeAll(institutesToRemove);
	}

	private boolean hasShiftedImportNumberWithSameContent(MatchedInstitute matchedInstitute) {
		return matchedInstitute.existing().getImportNumber() != matchedInstitute.toImport().getImportNumber()
				&& hasSameContent(matchedInstitute.existing(), matchedInstitute.toImport());
	}

	private void collectDuplicateImportNumberMatches(MatchedInstitute reference, List<MatchedInstitute> matchedInstitutes,
			List<MatchedInstitute> institutesToRemove) {
		for (MatchedInstitute candidate : matchedInstitutes) {
			if (!reference.equals(candidate)
					&& reference.existing().getImportNumber() == candidate.toImport().getImportNumber()
					&& hasSameContent(reference.existing(), candidate.toImport())) {
				institutesToRemove.add(candidate);
			}
		}
	}

	@Override
	protected void copyImportedFields(Institute existing, Institute toImport) {
		existing.setImportNumber(toImport.getImportNumber());
		existing.setBic(toImport.getBic());
		existing.setBankName(toImport.getBankName());
		existing.setPlace(toImport.getPlace());
		existing.setDataCenter(toImport.getDataCenter());
		existing.setOrganisation(toImport.getOrganisation());
		existing.setHbciDns(toImport.getHbciDns());
		existing.setHbciIp(toImport.getHbciIp());
		existing.setHbciVersion(toImport.getHbciVersion());
		existing.setDdv(toImport.getDdv());
		existing.setRdh1(toImport.getRdh1());
		existing.setRdh2(toImport.getRdh2());
		existing.setRdh3(toImport.getRdh3());
		existing.setRdh4(toImport.getRdh4());
		existing.setRdh5(toImport.getRdh5());
		existing.setRdh6(toImport.getRdh6());
		existing.setRdh7(toImport.getRdh7());
		existing.setRdh8(toImport.getRdh8());
		existing.setRdh9(toImport.getRdh9());
		existing.setRdh10(toImport.getRdh10());
		existing.setPinUrl(toImport.getPinUrl());
		existing.setVersion(toImport.getVersion());
		existing.setLastChanged(toImport.getLastChanged());
	}

	@Override
	protected Institute mapRecord(CSVRecord csvRecord) {
		Institute institute = createImportedInstitute();
		String nr = csvRecord.get("Nr.");

		if (nr == null || nr.isBlank() || !hasInstituteData(csvRecord, "Nr.")) {
			return null;
		}

		institute.setImportNumber(Integer.parseInt(nr));
		institute.setBlz(csvRecord.get("BLZ"));
		institute.setBic(csvRecord.get("BIC"));
		institute.setBankName(csvRecord.get("Institut"));
		institute.setPlace(csvRecord.get("Ort"));
		institute.setDataCenter(csvRecord.get("RZ"));
		institute.setOrganisation(csvRecord.get("Organisation"));
		institute.setHbciDns(csvRecord.get("HBCI-Zugang DNS"));
		institute.setHbciIp(csvRecord.get("HBCI- Zugang     IP-Adresse"));

		String hbciVersion = csvRecord.get("HBCI-Version");
		if (!hbciVersion.isEmpty()) {
			institute.setHbciVersion(Double.valueOf(hbciVersion));
		}

		institute.setDdv(csvRecord.get("DDV"));
		institute.setRdh1(TypeConverter.toBoolean(csvRecord.get("RDH-1")));
		institute.setRdh2(TypeConverter.toBoolean(csvRecord.get("RDH-2")));
		institute.setRdh3(TypeConverter.toBoolean(csvRecord.get("RDH-3")));
		institute.setRdh4(TypeConverter.toBoolean(csvRecord.get("RDH-4")));
		institute.setRdh5(TypeConverter.toBoolean(csvRecord.get("RDH-5")));
		institute.setRdh6(TypeConverter.toBoolean(csvRecord.get("RDH-6")));
		institute.setRdh7(TypeConverter.toBoolean(csvRecord.get("RDH-7")));
		institute.setRdh8(TypeConverter.toBoolean(csvRecord.get("RDH-8")));
		institute.setRdh9(TypeConverter.toBoolean(csvRecord.get("RDH-9")));
		institute.setRdh10(TypeConverter.toBoolean(csvRecord.get("RDH-10")));
		institute.setPinUrl(csvRecord.get("PIN/TAN-Zugang URL"));
		institute.setVersion(csvRecord.get("Version"));

		String date = csvRecord.get("Datum letzte Änderung");
		if (!date.isEmpty()) {
			institute.setLastChanged(TypeConverter.toLocalDateFromDateStr(date));
		}

		return institute;
	}

	@Override
	protected boolean hasSameContent(Institute a, Institute b) {
		return Objects.equals(a.getBlz(), b.getBlz()) && Objects.equals(a.getBic(), b.getBic()) && Objects.equals(a.getBankName(), b.getBankName())
				&& Objects.equals(a.getPlace(), b.getPlace()) && Objects.equals(a.getDataCenter(), b.getDataCenter())
				&& Objects.equals(a.getOrganisation(), b.getOrganisation()) && Objects.equals(a.getHbciDns(), b.getHbciDns())
				&& Objects.equals(a.getHbciIp(), b.getHbciIp()) && Objects.equals(a.getHbciVersion(), b.getHbciVersion())
				&& Objects.equals(a.getDdv(), b.getDdv()) && Objects.equals(a.getRdh1(), b.getRdh1()) && Objects.equals(a.getRdh2(), b.getRdh2())
				&& Objects.equals(a.getRdh3(), b.getRdh3()) && Objects.equals(a.getRdh4(), b.getRdh4()) && Objects.equals(a.getRdh5(), b.getRdh5())
				&& Objects.equals(a.getRdh6(), b.getRdh6()) && Objects.equals(a.getRdh7(), b.getRdh7()) && Objects.equals(a.getRdh8(), b.getRdh8())
				&& Objects.equals(a.getRdh9(), b.getRdh9()) && Objects.equals(a.getRdh10(), b.getRdh10())
				&& Objects.equals(a.getPinUrl(), b.getPinUrl()) && Objects.equals(a.getVersion(), b.getVersion())
				&& Objects.equals(getTime(a.getLastChanged()), getTime(b.getLastChanged()));
	}

	@Override
	protected boolean isSameInstituteIdentity(Institute a, Institute b) {
		return Objects.equals(a.getBlz(), b.getBlz()) && Objects.equals(a.getBic(), b.getBic()) && Objects.equals(a.getBankName(), b.getBankName())
				&& Objects.equals(a.getPlace(), b.getPlace());
	}

	private Long getTime(LocalDate cal) {
		return cal == null ? null : cal.toEpochDay();
	}

	static Charset getCharset(Charset charset) {
		return charset != null ? charset : StandardCharsets.ISO_8859_1;
	}

}
