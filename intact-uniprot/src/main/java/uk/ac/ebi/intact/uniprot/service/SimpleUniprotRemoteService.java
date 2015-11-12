package uk.ac.ebi.intact.uniprot.service;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import uk.ac.ebi.intact.uniprot.model.*;
import uk.ac.ebi.intact.uniprot.model.Organism;
import uk.ac.ebi.intact.uniprot.service.crossRefAdapter.ReflectionCrossReferenceBuilder;
import uk.ac.ebi.intact.uniprot.service.crossRefAdapter.UniprotCrossReference;
import uk.ac.ebi.intact.uniprot.service.referenceFilter.CrossReferenceFilter;
import uk.ac.ebi.kraken.interfaces.uniprot.*;
import uk.ac.ebi.kraken.interfaces.uniprot.comments.*;
import uk.ac.ebi.kraken.interfaces.uniprot.description.Field;
import uk.ac.ebi.kraken.interfaces.uniprot.description.FieldType;
import uk.ac.ebi.kraken.interfaces.uniprot.description.Name;
import uk.ac.ebi.kraken.interfaces.uniprot.features.*;
import uk.ac.ebi.kraken.interfaces.uniprot.genename.GeneNameSynonym;
import uk.ac.ebi.kraken.interfaces.uniprot.genename.ORFName;
import uk.ac.ebi.kraken.interfaces.uniprot.genename.OrderedLocusName;
import uk.ac.ebi.uniprot.dataservice.client.Client;
import uk.ac.ebi.uniprot.dataservice.client.QueryResult;
import uk.ac.ebi.uniprot.dataservice.client.ServiceFactory;
import uk.ac.ebi.uniprot.dataservice.client.exception.ServiceException;
import uk.ac.ebi.uniprot.dataservice.client.uniprot.UniProtQueryBuilder;
import uk.ac.ebi.uniprot.dataservice.client.uniprot.UniProtService;
import uk.ac.ebi.uniprot.dataservice.query.Query;

import java.util.*;

/**
 * This service is a uniprot service which DOES not keep any uniprot protein in memory. It does not use any cache at all.
 *
 * @author Marine Dumousseau (marine@ebi.ac.uk)
 * @version $Id$
 * @since <pre>31/10/11</pre>
 */

public class SimpleUniprotRemoteService extends AbstractUniprotService {

    /**
     * Sets up a logger for that class.
     */
    public static final Log log = LogFactory.getLog(UniprotRemoteService.class);

    protected UniProtService uniProtQueryService;

    protected final static String FEATURE_CHAIN_FIELD = "feature.chain:";
    protected final static String FEATURE_PEPTIDE_FIELD = "feature.peptide:";
    protected final static String FEATURE_PRO_PEPTIDE_FIELD = "feature.propep:";

    public SimpleUniprotRemoteService() {
        super();
//        uniProtQueryService = UniProtJAPI.factory.getUniProtQueryService();
        ServiceFactory serviceFactoryInstance = Client.getServiceFactoryInstance();
        uniProtQueryService = serviceFactoryInstance.getUniProtQueryService();
    }

    public SimpleUniprotRemoteService(CrossReferenceFilter filter) {
        super(filter);
        ServiceFactory serviceFactoryInstance = Client.getServiceFactoryInstance();
        uniProtQueryService = serviceFactoryInstance.getUniProtQueryService();
//        uniProtQueryService = UniProtJAPI.factory.getUniProtQueryService();
    }

    public Collection<UniprotProtein> retrieve( String ac ) {
        return retrieve(ac, true);
    }

    /**
     * Retrieves proteins and variants (protein transcripts) for the accession provided. It is the combination of calling
     * the retrieve(ac) and the retriveProteinTranscripts(ac) methods.
     * @param ac the accession to search
     * @return proteins or variants, using an interface common to the UniprotProtein and UniprotProteinTranscript classes.
     */
    public Collection<UniprotProteinLike> retrieveAny( String ac ) {
        Collection<UniprotProteinLike> protsAndVariants = new ArrayList<UniprotProteinLike>();
        protsAndVariants.addAll(retrieve(ac));
        protsAndVariants.addAll(retrieveProteinTranscripts(ac));

        return protsAndVariants;
    }

    public Collection<UniprotProteinTranscript> retrieveProteinTranscripts( String ac ){
        if (log.isDebugEnabled()) {
            log.debug("Retrieving splice variants from UniProt: "+ac);
        }
        Collection<UniprotProteinTranscript> variants = new ArrayList<UniprotProteinTranscript>();

        variants.addAll(retrieveSpliceVariant(ac));
        variants.addAll(retrieveFeatureChain(ac));

        return variants;
    }

    public Collection<UniprotSpliceVariant> retrieveSpliceVariant( String ac ) {
        if (log.isDebugEnabled()) {
            log.debug("Retrieving splice variants from UniProt: "+ac);
        }
        Collection<UniprotSpliceVariant> variants = new ArrayList<UniprotSpliceVariant>();
        Collection<String> variantAcProcessed = new ArrayList<String>();

        Collection<UniprotProtein> proteins = new ArrayList<UniprotProtein>();

        Iterator<UniProtEntry> it = getUniProtEntry( ac );

        if ( !it.hasNext() ) {
            // we didn't find anything
            addError( ac, new UniprotServiceReport( "Could not find splice variants: " + ac ) );
        }

        while ( it.hasNext() ) {
            UniProtEntry uniProtEntry = it.next();
            UniprotProtein uniprotProtein = buildUniprotProtein( uniProtEntry, true );
            proteins.add( uniprotProtein );
            UniprotSpliceVariant variant = retrieveUniprotSpliceVariant(uniprotProtein, ac);

            if (variant != null){
                if (!variantAcProcessed.contains(variant.getPrimaryAc())){
                    variants.add(variant);
                    variantAcProcessed.add(variant.getPrimaryAc());
                    variant.setMasterProtein(uniprotProtein);
                }
            }
        }

        return variants;
    }

    public Collection<UniprotFeatureChain> retrieveFeatureChain( String ac ) {
        if (log.isDebugEnabled()) {
            log.debug("Retrieving feature chains from UniProt: "+ac);
        }
        Collection<UniprotFeatureChain> variants = new ArrayList<UniprotFeatureChain>();

        Collection<UniprotProtein> proteins = new ArrayList<UniprotProtein>();

        Iterator<UniProtEntry> it = getUniProtEntry( ac );

        if ( !it.hasNext() ) {
            // we didn't find anything
            addError( ac, new UniprotServiceReport( "Could not find splice variants: " + ac ) );
        }

        while ( it.hasNext() ) {
            UniProtEntry uniProtEntry = it.next();
            UniprotProtein uniprotProtein = buildUniprotProtein( uniProtEntry, true );
            proteins.add( uniprotProtein );
            UniprotFeatureChain variant = retrieveUniprotFeatureChain(uniprotProtein, ac);

            if (variant != null) {
                variants.add(variant);
                variant.setMasterProtein(uniprotProtein);
            }
        }

        return variants;
    }

    public Collection<UniprotProtein> retrieve( String ac, boolean processSpliceVars ) {
        if (log.isDebugEnabled()) {
            log.debug("Retrieving from UniProt: "+ac);
        }
        Collection<UniprotProtein> proteins = new ArrayList<UniprotProtein>();

        Iterator<UniProtEntry> it = getUniProtEntry( ac );

        if ( !it.hasNext() ) {
            // we didn't find anything
            addError( ac, new UniprotServiceReport( "Could not find protein: " + ac ) );
        }

        while ( it.hasNext() ) {
            UniProtEntry uniProtEntry = it.next();
            proteins.add( buildUniprotProtein( uniProtEntry, processSpliceVars ) );
        }

        return proteins;
    }

    public Map<String, Collection<UniprotProtein>> retrieve( Collection<String> acs ) {
        return retrieve(acs, true);
    }

    public Map<String, Collection<UniprotProtein>> retrieve( Collection<String> acs, boolean processSpliceVars ) {

        if ( acs == null ) {
            throw new IllegalArgumentException( "You must give a non null List of UniProt ACs." );
        }

        if ( acs.isEmpty() ) {
            throw new IllegalArgumentException( "You must give a non empty List of UniProt ACs." );
        }

        Map<String, Collection<UniprotProtein>> results = new HashMap<String, Collection<UniprotProtein>>( acs.size() );

        // TODO use bulk loading and post sort the Map
        for ( String ac : acs ) {
            Collection<UniprotProtein> proteins = retrieve( ac, processSpliceVars );
            if ( proteins != null ) {
                results.put( ac, proteins );
            } else {
                addError( ac, new UniprotServiceReport( "Could not find protein for AC: " + ac ) );
            }
        }

        return results;
    }

    @Deprecated
    public Collection<UniprotProtein> retreive( String ac ) {
        return retrieve(ac);
    }

    @Deprecated
    public Map<String, Collection<UniprotProtein>> retreive( Collection<String> acs ) {
        return retrieve(acs);
    }

    //////////////////////////
    // private methods

    protected Iterator<UniProtEntry> getUniProtEntry( String ac ) {
        Iterator<UniProtEntry> iterator = null;

        String upperCaseAc = ac.toUpperCase();

        if ( IdentifierChecker.isSpliceVariantId( upperCaseAc ) ) {

            // we only use this search for splice variants
            Query query = UniProtQueryBuilder.id(upperCaseAc);
            try {
                iterator = uniProtQueryService.getEntries(query);
            } catch (ServiceException e) {
                e.printStackTrace();
            }

        }
        else if (IdentifierChecker.isFeatureChainId( upperCaseAc )){
            String acFixed = upperCaseAc;

            int index = upperCaseAc.indexOf(CHAIN_SEPARATOR);
            if (index != -1){
                acFixed = upperCaseAc.substring(index);
            }
            // we only use this search for feature chains

//            Query query = UniProtQueryBuilder.buildFullTextSearch( FEATURE_CHAIN_FIELD + acFixed + " OR " + FEATURE_PEPTIDE_FIELD + acFixed + " OR " + FEATURE_PRO_PEPTIDE_FIELD + acFixed );
            Query query = UniProtQueryBuilder.features(FeatureType.typeOf(FEATURE_CHAIN_FIELD), acFixed).or(UniProtQueryBuilder.features(FeatureType.typeOf(FEATURE_PEPTIDE_FIELD), acFixed)).or(UniProtQueryBuilder.features(FeatureType.typeOf(FEATURE_PRO_PEPTIDE_FIELD), acFixed));
            try {
                iterator = uniProtQueryService.getEntries(query);
            } catch (ServiceException e) {
                e.printStackTrace();
            }
        }
        else {
            iterator = getUniProtEntryForProteinEntry( upperCaseAc );
        }

        return iterator;
    }

    protected Iterator<UniProtEntry> getUniProtEntryForProteinEntry( String ac ) {
        // the Lucene field identifier copes with primary and secondary ACs.
//        String query = "identifier:" + ac;

        // search for primary and secondary ACs
        //String query = IndexField.PRIMARY_ACCESSION.getValue() + ":" + ac +
        //" OR " +
        //IndexField.UNIPROT_EXPIRED_IDENTIFIER.getValue() + ":" + ac +
        //" OR " +
        //IndexField.UNIPROT_ID.getValue() + ":" + ac;
        QueryResult<UniProtEntry> iterator = null;

        try {
            iterator = uniProtQueryService.getEntries( UniProtQueryBuilder.accession( ac ) );
        } catch (ServiceException e) {
            e.printStackTrace();
        }

        return iterator;
    }

    public static void main(String[] args) throws ServiceException {
        final UniProtService uniProtQueryService = Client.getServiceFactoryInstance().getUniProtQueryService();
//        final EntryIterator<UniProtEntry> iterator = uniProtQueryService.getEntryIterator(UniProtQueryBuilder.buildQuery("P43063"));
        final Query query = UniProtQueryBuilder.accession("P43063");
        final QueryResult<UniProtEntry> iterator = uniProtQueryService.getEntries(query);


        for (UniProtEntry protEntry : iterator.getCurrentPage().getResults()) {
            System.out.println(protEntry.getPrimaryUniProtAccession().getValue());
        }
    }

    protected UniprotProtein buildUniprotProtein( UniProtEntry uniProtEntry, boolean fetchSpliceVariants ) {

        // Process OS, OC, OX
        List<NcbiTaxonomyId> taxids = uniProtEntry.getNcbiTaxonomyIds();

        final uk.ac.ebi.kraken.interfaces.uniprot.Organism organism = uniProtEntry.getOrganism();
        String organismName = organism.getScientificName().getValue();
        String entryTaxid = taxids.get( 0 ).getValue();
        Organism o = new Organism( Integer.parseInt( entryTaxid ), organismName );
        o.setCommonName(organism.getCommonName().getValue());

        // extract parent's names
        if ( organism.hasCommonName() ) {
            o.getParents().add( organism.getCommonName().getValue() );
        }
        if ( organism.hasSynonym() ) {
            o.getParents().add( organism.getSynonym().getValue() );
        }

        String description = readDescription(uniProtEntry);

        UniprotProtein uniprotProtein = new UniprotProtein( uniProtEntry.getUniProtId().getValue(),
                uniProtEntry.getPrimaryUniProtAccession().getValue(),
                o,
                description);

        List<SecondaryUniProtAccession> secondaryAcs = uniProtEntry.getSecondaryUniProtAccessions();
        for ( SecondaryUniProtAccession secondaryAc : secondaryAcs ) {
            uniprotProtein.getSecondaryAcs().add( secondaryAc.getValue() );
        }

        // version of the entry
        uniprotProtein.setReleaseVersion( getSPTREntryReleaseVersion( uniProtEntry ) );
        uniprotProtein.setLastAnnotationUpdate( uniProtEntry.getEntryAudit().getLastAnnotationUpdateDate() );
        uniprotProtein.setLastSequenceUpdate( uniProtEntry.getEntryAudit().getLastSequenceUpdateDate() );

        // type of the entry
        if ( UniProtEntryType.SWISSPROT.equals( uniProtEntry.getType() ) ) {
            uniprotProtein.setSource( UniprotProteinType.SWISSPROT );
        } else if ( UniProtEntryType.TREMBL.equals( uniProtEntry.getType() ) ) {
            uniprotProtein.setSource( UniprotProteinType.TREMBL );
        } else if ( UniProtEntryType.UNKNOWN.equals( uniProtEntry.getType() ) ) {
            uniprotProtein.setSource( UniprotProteinType.UNKNOWN );
        } else {
            throw new IllegalStateException( "Only SWISSPROT, TREMBL and UNKNOWN source are supported: " +
                    uniProtEntry.getType().getValue() );
        }

        // Process gene names, orfs, synonyms, locus...
        processGeneNames( uniProtEntry, uniprotProtein );

        // add alternative full names
        for (Name name : uniProtEntry.getProteinDescription().getAlternativeNames()) {
            final List<Field> fullFields = name.getFieldsByType(FieldType.FULL);

            for (Field fullField : fullFields) {
                uniprotProtein.getSynomyms().add(fullField.getValue());
            }
        }

        // comments: function
        List<FunctionComment> functions = uniProtEntry.getComments( CommentType.FUNCTION );
        for ( FunctionComment function : functions ) {

            uniprotProtein.getFunctions().add( function.getValue() );
        }

        // Comments: disease
        List<DiseaseCommentStructured> diseases = uniProtEntry.getComments( CommentType.DISEASE );
        for ( DiseaseCommentStructured disease : diseases ) {
            uniprotProtein.getFunctions().add( disease.getDisease().getDescription().getValue() );
        }

        // keywords
        List<Keyword> keywords = uniProtEntry.getKeywords();
        for ( Keyword keyword : keywords ) {
            uniprotProtein.getKeywords().add( keyword.getValue() );
        }

        // Cross references
        processCrossReference( uniProtEntry, uniprotProtein );

        // sequence
        uniprotProtein.setSequence( uniProtEntry.getSequence().getValue() );
        uniprotProtein.setSequenceLength( uniProtEntry.getSequence().getLength() );
        uniprotProtein.setCrc64( uniProtEntry.getSequence().getCRC64() );
        // TODO molecular weight ?!

        // splice variants
        if (fetchSpliceVariants) {
            processSpliceVariants( uniProtEntry, uniprotProtein );
            //I commented this line because we not making any use of uniprot features in IntAct. But in case we use them later,
            // I have let the processFeatureChain method.
            // chains
            processFeatureChain( uniProtEntry, uniprotProtein );
            // feature peptides to be processed as feature chains
            processFeaturePeptide(uniProtEntry, uniprotProtein);
            // feature pro-peptides to be processed as feature chains
            processFeatureProPeptide(uniProtEntry, uniprotProtein);
        }

        return uniprotProtein;
    }

    protected String readDescription(UniProtEntry uniProtEntry) {
        String desc = null;

        if (uniProtEntry.getProteinDescription().hasRecommendedName()) {
            final List<Field> fullFields = uniProtEntry.getProteinDescription().getRecommendedName().getFieldsByType(FieldType.FULL);

            if (!fullFields.isEmpty()) {
                desc = fullFields.get(0).getValue();
            }
        }

        return desc;
    }

    /**
     * Extract from the SPTREntry the annotation release and the entry type, then combine them to get a version we will
     * use in the Xref. uniprot, identity )
     *
     * @param sptrEntry the entry from which we extract the information.
     *
     * @return a version as a String.
     */
    protected String getSPTREntryReleaseVersion( UniProtEntry sptrEntry ) {
        String version = null;
        String uniprotRelease = String.valueOf( sptrEntry.getEntryAudit().getEntryVersion() );
        //System.out.println( "uniprotRelease = " + uniprotRelease );
        if ( sptrEntry.getType().equals( UniProtEntryType.SWISSPROT ) ) {
            version = SWISS_PROT_PREFIX + uniprotRelease;
        } else if ( sptrEntry.getType().equals( UniProtEntryType.TREMBL ) ) {
            // will allow Version up to 999 ... then it will be truncated as Xref.dbRelease is VARCHAR2(10)
            version = TREMBL_PREFIX + uniprotRelease;
        } else {
            // though should not happen.
            log.warn( "Unexpected SPTREntry type: " + sptrEntry.getType().getValue() );
            version = uniprotRelease;
        }

        return version;
    }

    protected void processFeatureChain( UniProtEntry uniProtEntry, UniprotProtein protein ) {
        Collection<ChainFeature> features = uniProtEntry.getFeatures( FeatureType.CHAIN );

        for ( ChainFeature featureChain : features ) {

            String id = uniProtEntry.getPrimaryUniProtAccession().getValue() + "-" + featureChain.getFeatureId().getValue();
            // when uniprot does not know where is the start or the end of the protein the value will be -1
            // Getting a sequence from -1 to x throw an Exception take into account this exception.

            String description = featureChain.getFeatureDescription().getValue();

            FeatureLocation location = featureChain.getFeatureLocation();
            int begin = location.getStart();
            int end = location.getEnd();

            if( end > 0 && begin > 0 && end < begin ) {
                throw new IllegalArgumentException( "Unexpected feature location boundaries of chain "+
                        featureChain.getFeatureId() +" for parent " +
                        uniProtEntry.getPrimaryUniProtAccession() +
                        ": ["+begin+", "+ end +"]" );
            }

            final String sequence = protein.getSequence();
            if( sequence.length() < end ) {
                throw new IllegalArgumentException( "The AA sequence (length:"+ sequence.length() +") of parent " +
                        uniProtEntry.getPrimaryUniProtAccession() + " doesn't match the" +
                        " boundaried of feature chain "+ featureChain.getFeatureId() +
                        ": ["+begin+", "+ end +"]" );
            }

            String chainSequence = null;

            if (begin != -1 && end != -1){
                chainSequence = sequence.substring( begin - 1, end );
            }

            UniprotFeatureChain chain = new UniprotFeatureChain( id, protein.getOrganism(), chainSequence );
            chain.setDescription( description );
            chain.setStart( begin );
            chain.setEnd( end );

            // add the chain to the protein
            protein.getFeatureChains().add( chain );
        }
    }

    protected void processFeaturePeptide( UniProtEntry uniProtEntry, UniprotProtein protein ) {
        Collection<PeptideFeature> features = uniProtEntry.getFeatures( FeatureType.PEPTIDE );

        for ( PeptideFeature featurePeptide : features ) {

            String id = uniProtEntry.getPrimaryUniProtAccession().getValue() + "-" + featurePeptide.getFeatureId().getValue();
            // when uniprot does not know where is the start or the end of the protein the value will be -1
            // Getting a sequence from -1 to x throw an Exception take into account this exception.

            String description = featurePeptide.getFeatureDescription().getValue();

            FeatureLocation location = featurePeptide.getFeatureLocation();
            int begin = location.getStart();
            int end = location.getEnd();

            if( end > 0 && begin > 0 && end < begin ) {
                throw new IllegalArgumentException( "Unexpected feature location boundaries of peptide "+
                        featurePeptide.getFeatureId() +" for parent " +
                        uniProtEntry.getPrimaryUniProtAccession() +
                        ": ["+begin+", "+ end +"]" );
            }

            final String sequence = protein.getSequence();
            if( sequence.length() < end ) {
                throw new IllegalArgumentException( "The AA sequence (length:"+ sequence.length() +") of parent " +
                        uniProtEntry.getPrimaryUniProtAccession() + " doesn't match the" +
                        " boundaried of feature peptide "+ featurePeptide.getFeatureId() +
                        ": ["+begin+", "+ end +"]" );
            }

            String chainSequence = null;

            if (begin != -1 && end != -1){
                chainSequence = sequence.substring( begin - 1, end );
            }

            UniprotFeatureChain chain = new UniprotFeatureChain( id, protein.getOrganism(), chainSequence );
            chain.setDescription( description );
            chain.setStart( begin );
            chain.setEnd( end );

            // add the chain to the protein
            protein.getFeatureChains().add( chain );
        }
    }

    protected void processFeatureProPeptide( UniProtEntry uniProtEntry, UniprotProtein protein ) {
        Collection<ProPepFeature> features = uniProtEntry.getFeatures( FeatureType.PROPEP );

        for ( ProPepFeature proPep : features ) {

            String id = uniProtEntry.getPrimaryUniProtAccession().getValue() + "-" + proPep.getFeatureId().getValue();
            // when uniprot does not know where is the start or the end of the protein the value will be -1
            // Getting a sequence from -1 to x throw an Exception take into account this exception.

            String description = proPep.getFeatureDescription().getValue();

            FeatureLocation location = proPep.getFeatureLocation();
            int begin = location.getStart();
            int end = location.getEnd();

            if( end > 0 && begin > 0 && end < begin ) {
                throw new IllegalArgumentException( "Unexpected feature location boundaries of peptide "+
                        proPep.getFeatureId() +" for parent " +
                        uniProtEntry.getPrimaryUniProtAccession() +
                        ": ["+begin+", "+ end +"]" );
            }

            final String sequence = protein.getSequence();
            if( sequence.length() < end ) {
                throw new IllegalArgumentException( "The AA sequence (length:"+ sequence.length() +") of parent " +
                        uniProtEntry.getPrimaryUniProtAccession() + " doesn't match the" +
                        " boundaried of feature peptide "+ proPep.getFeatureId() +
                        ": ["+begin+", "+ end +"]" );
            }

            String chainSequence = null;

            if (begin != -1 && end != -1){
                chainSequence = sequence.substring( begin - 1, end );
            }

            UniprotFeatureChain chain = new UniprotFeatureChain( id, protein.getOrganism(), chainSequence );
            chain.setDescription( description );
            chain.setStart( begin );
            chain.setEnd( end );

            // add the chain to the protein
            protein.getFeatureChains().add( chain );
        }
    }

    protected void processGeneNames( UniProtEntry uniProtEntry, UniprotProtein protein ) {

        List<Gene> genes = uniProtEntry.getGenes();

        for ( Gene gene : genes ) {
            if (gene.hasGeneName() ) {
                protein.getGenes().add( gene.getGeneName().getValue() );
            }

            for ( GeneNameSynonym synonym : gene.getGeneNameSynonyms() ) {
                protein.getSynomyms().add( synonym.getValue() );
            }

            for ( ORFName orf : gene.getORFNames() ) {
                protein.getOrfs().add( orf.getValue() );
            }

            for ( OrderedLocusName locus : gene.getOrderedLocusNames() ) {
                protein.getLocuses().add( locus.getValue() );
            }
        }
    }

    protected Collection<UniprotCrossReference> convert( Collection<DatabaseCrossReference> refs ) {
        Collection<UniprotCrossReference> convertedRefs = new ArrayList<UniprotCrossReference>();
        ReflectionCrossReferenceBuilder builder = new ReflectionCrossReferenceBuilder();
        for ( DatabaseCrossReference ref : refs ) {
            String db = ref.getDatabase().getName();
            if ( getCrossReferenceSelector() != null && !getCrossReferenceSelector().isSelected( db ) ) {
                log.trace( getCrossReferenceSelector().getClass().getSimpleName() + " filtered out database: '" + db + "'." );
                continue;
            }

            for (UniprotCrossReference xref : builder.build(ref)) {

                if (xref != null){
                    convertedRefs.add( xref );
                }
            }

        }
        return convertedRefs;
    }

    protected void processCrossReference( UniProtEntry uniProtEntry, UniprotProtein protein ) {
        Collection<DatabaseCrossReference> databaseCrossReferences = uniProtEntry.getDatabaseCrossReferences();
        Collection<UniprotCrossReference> xrefs = convert( databaseCrossReferences );

        for ( UniprotCrossReference xref : xrefs ) {

            String ac = xref.getAccessionNumber();

            if (ac == null) {
                log.error("No AC could be found for xref: "+xref);
                continue;
            }

            String db = xref.getDatabase();

            String desc = xref.getDescription(); // TODO There is so far no straight forward way to process all cross refrence and extract descriptions. We could at least provide specific handlers in case we know we need to process specific databases.

            protein.getCrossReferences().add( new UniprotXref( ac, db, desc ) );

            // handles HUGE cross references (stored as gene name or synonym and start with KIAA)
            if ( getCrossReferenceSelector() != null &&
                    ( getCrossReferenceSelector().isSelected( "HUGE" ) || getCrossReferenceSelector().isSelected( "KIAA" ) ) ) {

                // we only do this if the filter requires it explicitely.
                List<Gene> genes = uniProtEntry.getGenes();
                for ( Gene gene : genes ) {

                    String geneName = gene.getGeneName().getValue();
                    if ( geneName.startsWith( "KIAA" ) ) {
                        protein.getCrossReferences().add( new UniprotXref( geneName, "HUGE" ) );
                    }

                    List<GeneNameSynonym> synonyms = gene.getGeneNameSynonyms();
                    for ( GeneNameSynonym synonym : synonyms ) {
                        String syn = synonym.getValue();
                        if ( syn.startsWith( "KIAA" ) ) {
                            protein.getCrossReferences().add( new UniprotXref( syn, "HUGE" ) );
                        }
                    }
                }
            }
        } // for Cross Ref
    }

    protected void processSpliceVariants( UniProtEntry uniProtEntry, UniprotProtein protein ) {

        Map<String,String> seqMap = new HashMap<String,String>();

        List<UniprotSpliceVariant> spliceVariants = findSpliceVariants(uniProtEntry, protein.getOrganism(), seqMap);

        // add the splice variant to the original protein
        protein.getSpliceVariants().addAll( spliceVariants );
    }

    protected List<UniprotSpliceVariant> findSpliceVariants(UniProtEntry uniProtEntry, Organism organism, Map<String, String> seqMap) {
        if (log.isDebugEnabled()) {
            log.debug("Finding splice variants for: " + uniProtEntry.getPrimaryUniProtAccession().getValue());
        }

        List<UniprotSpliceVariant> spliceVariants = new ArrayList<UniprotSpliceVariant>();

        List<AlternativeProductsComment> comments = uniProtEntry.getComments( CommentType.ALTERNATIVE_PRODUCTS );
        for ( AlternativeProductsComment comment : comments ) {
            List<AlternativeProductsIsoform> isoforms = comment.getIsoforms();

            for ( AlternativeProductsIsoform isoform : isoforms ) {

                List<String> ids = new ArrayList<String>();

                List<IsoformId> isoIDs = isoform.getIds();
                for ( IsoformId isoID : isoIDs ) {
                    //System.out.println("isoID  : " + isoID.getValue());


                    // TODO remove this once the API is fixed, currently when multiple ids are present they are returned as a comma separated value :(
                    for ( int i = 0; i < isoID.getValue().split( "," ).length; i++ ) {
                        String id = isoID.getValue().split( "," )[i].trim();
                        if ( log.isTraceEnabled() ) {
                            log.trace( "Found ID " + i + ":" + id );
                        }
                        ids.add( id );
                    }
                }

                // process alternative sequence
                String spliceVarId = ids.get(0);

                String sequence = null;

                if (seqMap.containsKey(spliceVarId)) {
                    sequence = seqMap.get(spliceVarId);
                } else {

                    String parentProtein = getUniProtAccFromSpliceVariantId(spliceVarId);

                    // check that the sequence is in the current entry

                    String status = isoform.getIsoformSequenceStatus().getValue();

                    if (log.isTraceEnabled()) log.trace("Sequence status: " + status);

                    switch (isoform.getIsoformSequenceStatus()) {
                        case NOT_DESCRIBED:
                            //log.error("According to uniprot the splice variant " + spliceVarId + " has no sequence (status = NOT_DESCRIBED)");
                        case DESCRIBED:
                            sequence = uniProtEntry.getSplicedSequence(isoform.getName().getValue());
                            break;
                        case DISPLAYED:
                            sequence = uniProtEntry.getSplicedSequence(isoform.getName().getValue());
                            break;
                        case EXTERNAL:
                            // then we need to load an external protein entry
                            log.warn("The alternative sequence '"+isoform.getName().getValue()+"' for '"+uniProtEntry.getPrimaryUniProtAccession().getValue()
                                    +"' has to be calculated on the basis of an external entry: " + parentProtein);

                            //sequence = uniProtEntry.getSplicedSequence(isoform.getName().getValue());

                            Iterator<UniProtEntry> iterator = getUniProtEntry(parentProtein);
                            int numberOfEntryInIterator = 0;
                            while (iterator.hasNext()) {

                                UniProtEntry uniprotEntryParentProtein = iterator.next();
                                //sequence = uniprotEntryParentProtein.getSplicedSequence(isoform.getName().getValue());

                                if (numberOfEntryInIterator >= 1) {
                                    // we were expecting to find only one protein - hopefully that should not happen !
                                    log.error("We were expecting to find only one protein while loading external sequence from: " + parentProtein);
                                    log.error("Found " + uniprotEntryParentProtein.getUniProtId());
                                    while (iterator.hasNext()) {
                                        UniProtEntry p = iterator.next();
                                        log.error("Found " + p.getUniProtId());
                                        sequence = null;
                                    }

                                } else {
                                    numberOfEntryInIterator++;
                                    sequence = uniprotEntryParentProtein.getSplicedSequence(isoform.getName().getValue());

                                    if (sequence == null || sequence.length() == 0 ) {
                                        for (UniprotSpliceVariant uniprotSpliceVariant : findSpliceVariants(uniprotEntryParentProtein, organism, seqMap)) {
                                            if (uniprotSpliceVariant.getPrimaryAc().equals(spliceVarId)) {
                                                sequence = uniprotSpliceVariant.getSequence();
                                                break;
                                            }
                                        }
                                    }

                                }
                                numberOfEntryInIterator++;
                            }
                    }
                }

                // build splice variant
                UniprotSpliceVariant sv = new UniprotSpliceVariant(spliceVarId,
                        organism,
                        sequence );
                // add secondary ids (if any)
                for ( int i = 1; i < ids.size(); i++ ) {
                    String id = ids.get( i );
                    sv.getSecondaryAcs().add( id );
                }

                // process synonyms
                List<IsoformSynonym> syns = isoform.getSynonyms();
                for ( IsoformSynonym syn : syns ) {
                    sv.getSynomyms().add( syn.getValue() );
                }

                // process note
                List<EvidencedValue> evidencedValues = isoform.getNote().getTexts();
                StringBuilder stringBuffer = new StringBuilder("");
                for (EvidencedValue evidencedValue : evidencedValues) {
                    stringBuffer.append(evidencedValue.getValue()).append(" ");
                }
                sv.setNote(stringBuffer.toString());

                spliceVariants.add(sv);
            } // for isoform
        } // for comments

        if (log.isDebugEnabled()) {
            log.debug("\tFound "+spliceVariants.size()+" splice variants");
        }

        return spliceVariants;
    }

    protected String getUniProtAccFromSpliceVariantId( String svId ) {
        int index = svId.indexOf( "-" );
        if ( index == -1 ) {
            throw new IllegalArgumentException( "The given accession number if not of a splice variant: " + svId );
        }
        return svId.substring( 0, index );
    }

    public void start() {
        uniProtQueryService.start();
    }

    public void close() {
        uniProtQueryService.stop();
    }
}
