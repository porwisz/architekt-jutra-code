# CalculateUnitFootprint — brakujące pokrycie testami

Rozmowa w parze między Janem (autorem FootprintFacade) a Anną (recenzującą PR), 6 maja 2026.

**14:02**
Anna
Hej, masz chwilę? Przechodzę przez Twój PR z FootprintFacade i wydaje mi się, że mamy słabe pokrycie testami dla CalculateUnitFootprint. Jest kilka ścieżek, których nie znajduję nigdzie w projekcie testów.

**14:02**
Jan
Tak, spodziewałem się, że ktoś to wyłapie. CalculateTotalFootprint ma przyzwoite pokrycie, ale tę unit'ową dorobiłem na końcu. Czego brakuje?

**14:03**
Anna
Zacznijmy od oczywistej rzeczy. Nie ma testu sprawdzającego, czy konwersja per-100g jest w ogóle poprawna. Metoda robi total / (MaterialWeightKg * 10), a tej arytmetyki nikt nigdzie nie weryfikuje.

**14:03**
Jan
Słusznie. Prosty przykład: jeśli MaterialWeightKg wynosi 0.5, a totalny breakdown wychodzi 2.0 kg CO2, to unit footprint powinien być 0.4. Dwa podzielone przez pięć.

**14:04**
Anna
Tak. I drugi punkt zaczepienia — 1.0 kg z totalem 5.0 powinno dać 0.5 per 100g. Te dwa razem przypinają wzór.

**14:04**
Jan
Dodałbym jeszcze trzeci z mniej okrągłą liczbą, żeby nie przepuścić przypadkiem błędnej implementacji, która tylko dzieli przez dwa. Coś jak 0.25 kg, total 0.6 kg CO2, oczekiwane 0.24.

**14:05**
Anna
Dobre. Następna luka. Guard clause — MaterialWeightKg <= 0 rzuca ArgumentException. Nie mamy nic, co to pokrywa. Potrzebujemy co najmniej przypadku zera i ściśle ujemnego.

**14:05**
Jan
Oba powinny rzucać ArgumentException, a nazwa parametru w wyjątku powinna być "parameters", a nie "MaterialWeightKg". Chcę, żeby test asertował też ParamName, bo wcześniej już mnie to ugryzło, jak się rozjechało.

**14:06**
Anna
Zgoda. Skoro już o guardach — null parameters powinno rzucać ArgumentNullException przez ArgumentNullException.ThrowIfNull. To też jest nieprzetestowane.

**14:06**
Jan
Tak. Trywialny test, ale warto go mieć, żeby refaktor guarda nie cofnął się po cichu do NullReferenceException.

**14:07**
Anna
Teraz trudniejsze. Co się dzieje, gdy total breakdown jest zerowy? Powiedzmy, że wszystkie komponenty są nie-aktywne dla danych parametrów, więc total wychodzi 0 kg CO2.

**14:07**
Jan
Wtedy unit footprint to zero. Arytmetyka nadal działa — 0 podzielone przez dodatnią liczbę to 0 — i nie rzucamy wyjątku. Myślę, że to akurat poprawne zachowanie, po prostu rzadkie.

**14:08**
Anna
Zgoda, że poprawne, ale potrzebuje jawnego testu, bo ktoś może to później „naprawić" guardem na pusty breakdown i złamać kontrakt. Przypnij to.

**14:08**
Jan
Słuszna uwaga. Dodam test, w którym Components jest puste, a MaterialWeightKg dodatnie — oczekiwane KgCo2(0).

**14:09**
Anna
A bardzo małe wagi? Jeśli MaterialWeightKg to 0.0001 — to ściśle dodatnia, więc guard przepuszcza — czy matematyka nadal daje sensowny wynik, czy wybuchamy na precyzji?

**14:09**
Jan
Używamy decimal od początku do końca, więc powinno być ok. Ale tak, test brzegowy na czymś jak 0.0001 kg z totalem powiedzmy 0.001 kg CO2 — powinien dać 1.0 per 100g. Warto przypiąć, żeby nikt po cichu nie przełączył tego na double.

**14:10**
Anna
Wspomnij o tym w nazwie testu. Coś jak CalculateUnitFootprint_PreservesPrecision_WithVerySmallMaterialWeight. Nazwy, które tłumaczą *dlaczego*, są tu warte złota.

**14:10**
Jan
Zrobię. Coś jeszcze?

**14:11**
Anna
Jeszcze jedna rzecz, którą chcę pokryć. Relacja między tymi dwiema metodami. CalculateUnitFootprint wewnętrznie woła CalculateTotalFootprint i jest niezmiennik: unit_footprint * MaterialWeightKg * 10 powinno równać się totalowi. Powinniśmy mieć test, który asertuje, że obie metody się zgadzają.

**14:11**
Jan
Masz na myśli test w stylu property-based? Te same parametry, odpalamy obie, sprawdzamy relację?

**14:12**
Anna
Tak. Nie musi być pełnym frameworkiem property-based, wystarczy jeden lub dwa zestawy parametrów, gdzie liczymy obie i asertujemy, że równanie się zgadza w granicach tolerancji decimal. To jest taki test, który łapie całą klasę bugów — np. jeśli ktoś „zoptymalizuje" CalculateUnitFootprint pomijając breakdown i licząc od zera, a wyjdzie mu subtelnie źle.

**14:12**
Jan
Dobry. Dodałbym też — skoro unit basis to MaterialWeightKg * 10, to ta dziesiątka jest magic number. Jeśli kiedyś ktoś zmieni definicję jednostki z per-100g na per-kg, test asertujący „0.5 kg, total 2.0, oczekiwane 0.4" walnie głośno. Więc to działa też jako regression guard na samą definicję jednostki.

**14:13**
Anna
Tak. Warto komentarz w teście tłumaczący, skąd 10. Inaczej ktoś przeczyta i pomyśli, że to arbitralne.

**14:13**
Jan
OK, lecę pisać. Podsumujmy — happy path arytmetyki z trzema parami waga/total, MaterialWeightKg = 0 rzuca, ujemne rzuca, null rzuca, pusty breakdown daje zero, bardzo mała waga zachowuje precyzję i test spójności łączący unit i total. W sumie siedem albo osiem testów.

**14:14**
Anna
Brzmi sensownie. Pomińmy framework property-based na razie, niech będą zwykłe XUnit cases. Jeśli później zechcemy dodać FsCheck albo coś podobnego, możemy wtedy podnieść ten test spójności.

**14:14**
Jan
Zgoda. Testy będę miał do końca jutra, wtedy ponownie poproszę o review.

**14:14**
Anna
Dzięki. Szczególnie testy guardów — nie chcę, żeby to wjeżdżało bez nich.
