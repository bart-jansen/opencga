package org.opencb.opencga.catalog.stats.solr.converters;

import org.apache.commons.lang3.NotImplementedException;
import org.opencb.commons.datastore.core.ComplexTypeConverter;
import org.opencb.opencga.catalog.stats.solr.IndividualSolrModel;
import org.opencb.opencga.core.common.TimeUtils;
import org.opencb.opencga.core.models.Individual;
import org.opencb.opencga.core.models.Study;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Created by wasim on 04/07/18.
 */
public class CatalogIndividualToSolrIndividualConverter implements ComplexTypeConverter<Individual, IndividualSolrModel> {

    private Study study;

    public CatalogIndividualToSolrIndividualConverter(Study study) {
        this.study = study;
    }

    @Override
    public Individual convertToDataModelType(IndividualSolrModel object) {
        throw new NotImplementedException("Operation not supported");
    }

    @Override
    public IndividualSolrModel convertToStorageType(Individual individual) {
        IndividualSolrModel individualSolrModel = new IndividualSolrModel();

        individualSolrModel.setUid(individual.getUid());
        individualSolrModel.setStudyId(study.getFqn().replace(":", "__"));

        individualSolrModel.setHasFather(individual.getFather() != null && individual.getFather().getUid() > 0);
        individualSolrModel.setHasMother(individual.getMother() != null && individual.getMother().getUid() > 0);
        if (individual.getMultiples() != null && individual.getMultiples().getSiblings() != null
                && !individual.getMultiples().getSiblings().isEmpty()) {
            individualSolrModel.setNumMultiples(individual.getMultiples().getSiblings().size());
            individualSolrModel.setMultiplesType(individual.getMultiples().getType());
        } else {
            individualSolrModel.setNumMultiples(0);
            individualSolrModel.setMultiplesType("None");
        }

        if (individual.getSex() != null) {
            individualSolrModel.setSex(individual.getSex().name());
        }

        if (individual.getKaryotypicSex() != null) {
            individualSolrModel.setKaryotypicSex(individual.getKaryotypicSex().name());
        }

        individualSolrModel.setEthnicity(individual.getEthnicity());

        if (individual.getPopulation() != null) {
            individualSolrModel.setPopulation(individual.getPopulation().getName());
        }
        individualSolrModel.setRelease(individual.getRelease());
        individualSolrModel.setVersion(individual.getVersion());

        Date date = TimeUtils.toDate(individual.getCreationDate());
        LocalDate localDate = date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();

        individualSolrModel.setCreationYear(localDate.getYear());
        individualSolrModel.setCreationMonth(localDate.getMonth().toString());
        individualSolrModel.setCreationDay(localDate.getDayOfMonth());
        individualSolrModel.setCreationDayOfWeek(localDate.getDayOfWeek().toString());
        individualSolrModel.setStatus(individual.getStatus().getName());

        if (individual.getStatus() != null) {
            individualSolrModel.setStatus(individual.getStatus().getName());
        }
        if (individual.getLifeStatus() != null) {
            individualSolrModel.setLifeStatus(individual.getLifeStatus().name());
        }
        if (individual.getAffectationStatus() != null) {
            individualSolrModel.setAffectationStatus(individual.getAffectationStatus().name());
        }
        individualSolrModel.setPhenotypes(SolrConverterUtil.populatePhenotypes(individual.getPhenotypes()));

        individualSolrModel.setNumSamples(individual.getSamples() != null ? individual.getSamples().size() : 0);

        individualSolrModel.setParentalConsanguinity(individual.isParentalConsanguinity());
        individualSolrModel.setAnnotations(SolrConverterUtil.populateAnnotations(individual.getAnnotationSets()));

        // Extract the permissions
        Map<String, Set<String>> individualAcl =
                SolrConverterUtil.parseInternalOpenCGAAcls((List<Map<String, Object>>) individual.getAttributes().get("OPENCGA_ACL"));
        List<String> effectivePermissions =
                SolrConverterUtil.getEffectivePermissions((Map<String, Set<String>>) study.getAttributes().get("OPENCGA_ACL"),
                        individualAcl, "INDIVIDUAL");
        individualSolrModel.setAcl(effectivePermissions);

        return individualSolrModel;
    }
}
