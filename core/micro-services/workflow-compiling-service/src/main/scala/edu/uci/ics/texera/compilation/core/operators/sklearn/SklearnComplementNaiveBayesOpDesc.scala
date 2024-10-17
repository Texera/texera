package edu.uci.ics.texera.compilation.core.operators.sklearn

class SklearnComplementNaiveBayesOpDesc extends SklearnMLOpDesc {
  override def getImportStatements = "from sklearn.naive_bayes import ComplementNB"
  override def getUserFriendlyModelName = "Complement Naive Bayes"
}
