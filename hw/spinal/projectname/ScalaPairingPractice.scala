package projectname

import scala.collection.mutable.ArrayBuffer

case class Person(name: String, male: Boolean, age: Int)

object ScalaPairingPractice extends App {
  def pairPeople(people: Seq[Person]): (Seq[(Person, Person)], Seq[Person], Seq[Person]) = {
    val girls = people.filter(!_.male).sortBy(_.age)
    val boys = people.filter(_.male).sortBy(_.age)
    val pairs = girls.zip(boys)

    (pairs, girls.drop(pairs.length), boys.drop(pairs.length))
  }

  val people = ArrayBuffer(
    Person("Alice", male = false, age = 20),
    Person("Bob", male = true, age = 24),
    Person("Cindy", male = false, age = 22),
    Person("David", male = true, age = 21),
    Person("Eve", male = false, age = 23)
  )

  val (pairs, unmatchedGirls, unmatchedBoys) = pairPeople(people.toList)

  println("ScalaPairingPractice result:")
  pairs.foreach { case (girl, boy) =>
    println(s"  ${girl.name} <-> ${boy.name}")
  }

  if (unmatchedGirls.nonEmpty) {
    println(s"  unmatched girls: ${unmatchedGirls.map(_.name).mkString(", ")}")
  }
  if (unmatchedBoys.nonEmpty) {
    println(s"  unmatched boys: ${unmatchedBoys.map(_.name).mkString(", ")}")
  }

  assert(pairs.map { case (girl, boy) => girl.name -> boy.name } == Seq("Alice" -> "David", "Cindy" -> "Bob"))
  assert(unmatchedGirls.map(_.name) == Seq("Eve"))
  assert(unmatchedBoys.isEmpty)

  println("ScalaPairingPractice PASS")
}
