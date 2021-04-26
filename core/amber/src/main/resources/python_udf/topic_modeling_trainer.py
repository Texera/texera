import gensim
import gensim.corpora as corpora
import pandas

import texera_udf_operator_base


class TopicModeling(texera_udf_operator_base.TexeraBlockingUnsupervisedTrainerOperator):

    def open(self, *args):
        super(TopicModeling, self).open(*args)

        # TODO: _train_args from user input args
        if len(args) >= 3:
            self._train_args = {"num_topics": int(args[1])}
        else:
            self._train_args = {"num_topics": 5}

    # override accept to accept rows as lists
    def accept(self, row: pandas.Series, nth_child: int = 0) -> None:
        self._data.append(row[0].strip().split())

    @staticmethod
    def train(data, *args, **kwargs):
        # Create Dictionary
        id2word = corpora.Dictionary(data)

        # Create Corpus
        texts = data

        # Term Document Frequency
        corpus = [id2word.doc2bow(text1) for text1 in texts]

        lda_model = gensim.models.ldamodel.LdaModel(corpus=corpus,
                                                    id2word=id2word,
                                                    num_topics=kwargs["num_topics"],
                                                    random_state=100,
                                                    update_every=1,
                                                    chunksize=100,
                                                    passes=10,
                                                    alpha='auto',
                                                    per_word_topics=True)

        return lda_model

    def report(self, model):
        for id, topic in model.print_topics(num_topics=self._train_args["num_topics"]):
            self._result_tuples.append(pandas.Series({"output": topic}))


operator_instance = TopicModeling()
if __name__ == '__main__':
    '''
    The following lines can be put in the file and name it tokenized.txt:

    yes unfortunately use tobacco wrap
    nothing better coming home pre rolled blunt waiting work fact
    sell pre roll blunts
    dutch backwoods hemp wrap
    damn need wrap hemparillo cali fire please

    '''

    file1 = open("tokenized.txt", "r+")
    df = file1.readlines()

    operator_instance.open()
    for row in df:
        operator_instance.accept(pandas.Series([row]))
    operator_instance.input_exhausted()
    while operator_instance.has_next():
        print(operator_instance.next())

    file1.close()
    operator_instance.close()
