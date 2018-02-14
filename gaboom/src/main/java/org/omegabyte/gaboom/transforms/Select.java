package org.omegabyte.gaboom.transforms;

import org.apache.beam.sdk.transforms.DoFn;
import org.apache.beam.sdk.transforms.PTransform;
import org.apache.beam.sdk.transforms.ParDo;
import org.apache.beam.sdk.util.SerializableUtils;
import org.apache.beam.sdk.values.*;
import org.omegabyte.gaboom.Individuals;
import org.omegabyte.gaboom.SelectIndividuals;

import java.util.List;

/**
 * Select provides a template for creating individual selectors from a
 * population.
 */
public class Select {

    /**
     * SelectFn is a DoFn that returns the subset of a population along with
     * the selected indexes in a separate (and optional) output.  Individuals
     * from the initial population should be evaluated and sorted before being
     * passed to the SelectFn.
     * @param <GenomeT>
     */
    public static abstract class SelectFn<GenomeT> extends DoFn<KV<String, SelectIndividuals<GenomeT>>, KV<String, Individuals<GenomeT>>> {
        protected TupleTag<KV<String, List<Integer>>> selectIndicesTupleTag = new TupleTag<>();

        public void setSelectIndicesTupleTag(TupleTag<KV<String, List<Integer>>> selectIndicesTupleTag) {
            this.selectIndicesTupleTag = selectIndicesTupleTag;
        }
    }

    /**
     * SelectNoIndexTransform only returns the selected individuals from the
     * selector.
     * @param <GenomeT>
     */
    public static class SelectNoIndexTransform<GenomeT> extends PTransform<PCollection<KV<String, SelectIndividuals<GenomeT>>>, PCollection<KV<String, Individuals<GenomeT>>>> {
        private final SelectFn<GenomeT> fn;

        public SelectNoIndexTransform(SelectFn<GenomeT> fn) {
            this.fn = SerializableUtils.clone(fn);
        }

        @Override
        public PCollection<KV<String, Individuals<GenomeT>>> expand(PCollection<KV<String, SelectIndividuals<GenomeT>>> input) {
            return input.apply(ParDo.of(fn));
        }
    }

    /**
     * SelectIndexTransform returns multiple outputs including the population
     * selection and the selected indices.
     * @param <GenomeT>
     */
    public static class SelectIndexTransform<GenomeT> extends PTransform<PCollection<KV<String, SelectIndividuals<GenomeT>>>, PCollectionTuple> {
        private final TupleTag<KV<String, List<Integer>>> selectIndicesTupleTag;
        private final TupleTag<KV<String, Individuals<GenomeT>>> selectIndividualsTupleTag;
        private final SelectFn<GenomeT> fn;

        public SelectIndexTransform(TupleTag<KV<String, List<Integer>>> selectIndicesTupleTag, TupleTag<KV<String, Individuals<GenomeT>>> selectIndividualsTupleTag, SelectFn<GenomeT> fn) {
            this.selectIndicesTupleTag = selectIndicesTupleTag;
            this.selectIndividualsTupleTag = selectIndividualsTupleTag;
            this.fn = SerializableUtils.clone(fn);
            this.fn.selectIndicesTupleTag = selectIndicesTupleTag;
        }

        @Override
        public PCollectionTuple expand(PCollection<KV<String, SelectIndividuals<GenomeT>>> input) {
            return input.apply(ParDo.of(fn).withOutputTags(selectIndividualsTupleTag, TupleTagList.of(selectIndicesTupleTag)));
        }
    }

    /**
     * Provides a SingleOutput transform from the selector
     * @param selectFn
     * @param <GenomeT>
     * @return
     */
    public static <GenomeT> SelectNoIndexTransform<GenomeT> as(SelectFn<GenomeT> selectFn) {
        return new SelectNoIndexTransform<>(selectFn);
    }

    /**
     * Provides a MultiOutput transform from the selector
     * @param selectFn
     * @param idx
     * @param ind
     * @param <GenomeT>
     * @return
     */
    public static <GenomeT> SelectIndexTransform<GenomeT> as(SelectFn<GenomeT> selectFn, TupleTag<KV<String, List<Integer>>> idx, TupleTag<KV<String, Individuals<GenomeT>>> ind) {
        return new SelectIndexTransform<>(idx, ind, selectFn);
    }
}