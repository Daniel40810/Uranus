-- =====================================================================
-- Uranus / Schema DEMO
-- 09 - Szenarien der Spielwiese
-- Erzeugt von tools/UraPlayGen: Zustandsvektoren relativ zu Uranus, ICRF (km, km/s). Nicht von Hand ändern.
-- =====================================================================

BEGIN
  ura_api.put_scenario(p_code => 'HEUTE', p_title => 'Heute, frei',
    p_description => 'Der echte Stand vom 23. September 2026, nur weitergerechnet: die Hauptmonde mit Masse, die Irregulären als Testkörper, die Sonne als Störer. Kleine Kreise zeigen, wo die Ephemeride die Monde sieht — der Abstand ist die ehrliche Probe der Rechnung.',
    p_start_jd => 2461306.5, p_rate => 10, p_origin => 'BUILTIN', p_compare => 'J');
  ura_api.put_scenario_body('HEUTE', 1, 'Miranda', 'MOON', 6.400000e+19, 'N', 235.8,
    -106066.532212, 48150.827080, -57418.186562, -3.342627055105, -0.511970265682, 5.760279850001);
  ura_api.put_scenario_body('HEUTE', 2, 'Ariel', 'MOON', 1.251000e+21, 'N', 578.9,
    161603.262534, -8117.301347, -101681.244042, -2.698070105277, 1.839301634395, -4.430721797068);
  ura_api.put_scenario_body('HEUTE', 3, 'Umbriel', 'MOON', 1.275000e+21, 'N', 584.7,
    -135689.423169, -32378.937608, 225247.668127, 3.903893698308, -1.466143196544, 2.136277865199);
  ura_api.put_scenario_body('HEUTE', 4, 'Titania', 'MOON', 3.400000e+21, 'N', 788.4,
    -420374.406258, 66970.986277, 99052.413181, 0.628232852727, -1.084461861863, 3.415815498550);
  ura_api.put_scenario_body('HEUTE', 5, 'Oberon', 'MOON', 3.076000e+21, 'N', 761.4,
    -567262.238715, 99685.141731, 99731.884352, 0.362993956217, -0.907366185093, 2.989829762549);
  ura_api.put_scenario_body('HEUTE', 6, 'Francisco', 'TEST', 7.247864e+15, 'J', 11,
    -2722003.400692, 4010116.328049, 403610.744155, 0.664586986561, 0.328359426989, 0.690909024649);
  ura_api.put_scenario_body('HEUTE', 7, 'Caliban', 'TEST', 2.540619e+17, 'J', 36,
    -3244319.280239, -2281674.241018, -4661267.441506, -0.798068174323, 0.354793562015, 0.571064618825);
  ura_api.put_scenario_body('HEUTE', 8, 'Stephano', 'TEST', 2.230447e+16, 'J', 16,
    192454.893013, 3810610.933766, 6299881.490083, 0.892378766366, 0.202819014790, 0.090602697449);
  ura_api.put_scenario_body('HEUTE', 9, 'S/2023 U1', 'TEST', 3.485073e+14, 'J', 4,
    5586600.585079, -3390201.011699, -6622409.311694, -0.307314763692, -0.640392326316, -0.123062074657);
  ura_api.put_scenario_body('HEUTE', 10, 'Trinculo', 'TEST', 3.969716e+15, 'J', 9,
    -9105172.969318, -4330757.394301, -2385096.800420, -0.297278874558, 0.467002753988, 0.360871931225);
  ura_api.put_scenario_body('HEUTE', 11, 'Sycorax', 'TEST', 2.297290e+18, 'J', 75,
    408580.798490, 15128521.365508, 6758038.445431, 0.402315340645, 0.233364014318, -0.080455449286);
  ura_api.put_scenario_body('HEUTE', 12, 'Margaret', 'TEST', 5.445427e+15, 'J', 10,
    10673238.645247, -1617430.465252, -12885408.461788, 0.108706339913, 0.052242461289, 0.532770657311);
  ura_api.put_scenario_body('HEUTE', 13, 'Prospero', 'TEST', 8.508480e+16, 'J', 25,
    14494631.298480, -17017379.450682, -3814036.251261, -0.322547617024, -0.191184392674, 0.107462686312);
  ura_api.put_scenario_body('HEUTE', 14, 'Setebos', 'TEST', 7.527759e+16, 'J', 24,
    10271000.796104, -939831.722514, -6117756.344146, -0.576685149252, -0.548464036483, -0.081635715078);
  ura_api.put_scenario_body('HEUTE', 15, 'Ferdinand', 'TEST', 5.445427e+15, 'J', 10,
    -18510052.287780, -19595396.671044, -8796435.153558, -0.244085879316, 0.198487340458, 0.159169496316);
END;
/

BEGIN
  ura_api.put_scenario(p_code => 'TITANIA', p_title => 'Schwere Titania',
    p_description => 'Titania mit hundertfacher Masse: 3,4·10²³ kg, das 4,6-Fache unseres Mondes. Die Mulde um sie wird tief; die Kreise zeigen, wo die Monde ohne den Eingriff stünden.',
    p_start_jd => 2461306.5, p_rate => 10, p_origin => 'BUILTIN', p_compare => 'J');
  ura_api.put_scenario_body('TITANIA', 1, 'Miranda', 'MOON', 6.400000e+19, 'N', 235.8,
    -106066.532212, 48150.827080, -57418.186562, -3.342627055105, -0.511970265682, 5.760279850001);
  ura_api.put_scenario_body('TITANIA', 2, 'Ariel', 'MOON', 1.251000e+21, 'N', 578.9,
    161603.262534, -8117.301347, -101681.244042, -2.698070105277, 1.839301634395, -4.430721797068);
  ura_api.put_scenario_body('TITANIA', 3, 'Umbriel', 'MOON', 1.275000e+21, 'N', 584.7,
    -135689.423169, -32378.937608, 225247.668127, 3.903893698308, -1.466143196544, 2.136277865199);
  ura_api.put_scenario_body('TITANIA', 4, 'Titania', 'MOON', 3.400000e+23, 'N', 788.4,
    -420374.406258, 66970.986277, 99052.413181, 0.628232852727, -1.084461861863, 3.415815498550);
  ura_api.put_scenario_body('TITANIA', 5, 'Oberon', 'MOON', 3.076000e+21, 'N', 761.4,
    -567262.238715, 99685.141731, 99731.884352, 0.362993956217, -0.907366185093, 2.989829762549);
  ura_api.put_scenario_body('TITANIA', 6, 'Francisco', 'TEST', 7.247864e+15, 'J', 11,
    -2722003.400692, 4010116.328049, 403610.744155, 0.664586986561, 0.328359426989, 0.690909024649);
  ura_api.put_scenario_body('TITANIA', 7, 'Caliban', 'TEST', 2.540619e+17, 'J', 36,
    -3244319.280239, -2281674.241018, -4661267.441506, -0.798068174323, 0.354793562015, 0.571064618825);
  ura_api.put_scenario_body('TITANIA', 8, 'Stephano', 'TEST', 2.230447e+16, 'J', 16,
    192454.893013, 3810610.933766, 6299881.490083, 0.892378766366, 0.202819014790, 0.090602697449);
  ura_api.put_scenario_body('TITANIA', 9, 'S/2023 U1', 'TEST', 3.485073e+14, 'J', 4,
    5586600.585079, -3390201.011699, -6622409.311694, -0.307314763692, -0.640392326316, -0.123062074657);
  ura_api.put_scenario_body('TITANIA', 10, 'Trinculo', 'TEST', 3.969716e+15, 'J', 9,
    -9105172.969318, -4330757.394301, -2385096.800420, -0.297278874558, 0.467002753988, 0.360871931225);
  ura_api.put_scenario_body('TITANIA', 11, 'Sycorax', 'TEST', 2.297290e+18, 'J', 75,
    408580.798490, 15128521.365508, 6758038.445431, 0.402315340645, 0.233364014318, -0.080455449286);
  ura_api.put_scenario_body('TITANIA', 12, 'Margaret', 'TEST', 5.445427e+15, 'J', 10,
    10673238.645247, -1617430.465252, -12885408.461788, 0.108706339913, 0.052242461289, 0.532770657311);
  ura_api.put_scenario_body('TITANIA', 13, 'Prospero', 'TEST', 8.508480e+16, 'J', 25,
    14494631.298480, -17017379.450682, -3814036.251261, -0.322547617024, -0.191184392674, 0.107462686312);
  ura_api.put_scenario_body('TITANIA', 14, 'Setebos', 'TEST', 7.527759e+16, 'J', 24,
    10271000.796104, -939831.722514, -6117756.344146, -0.576685149252, -0.548464036483, -0.081635715078);
  ura_api.put_scenario_body('TITANIA', 15, 'Ferdinand', 'TEST', 5.445427e+15, 'J', 10,
    -18510052.287780, -19595396.671044, -8796435.153558, -0.244085879316, 0.198487340458, 0.159169496316);
END;
/

BEGIN
  ura_api.put_scenario(p_code => 'MIRANDA', p_title => 'Miranda fällt',
    p_description => 'Miranda wird an ihrer Stelle abgebremst, sodass ihr Perizentrum bei 45 000 km liegt — unter der Roche-Grenze für ihre Dichte (63.689 km). Beim ersten Durchgang zerbricht sie; die Trümmer ziehen sich über die Umläufe zum Ring.',
    p_start_jd => 2461306.5, p_rate => 0.1, p_origin => 'BUILTIN', p_compare => 'J');
  ura_api.put_scenario_body('MIRANDA', 1, 'Miranda', 'MOON', 6.400000e+19, 'N', 235.8,
    -106066.532212, 48150.827080, -57418.186562, -2.401849761629, -0.365532936906, 4.130314384221);
  ura_api.put_scenario_body('MIRANDA', 2, 'Ariel', 'MOON', 1.251000e+21, 'N', 578.9,
    161603.262534, -8117.301347, -101681.244042, -2.698070105277, 1.839301634395, -4.430721797068);
  ura_api.put_scenario_body('MIRANDA', 3, 'Umbriel', 'MOON', 1.275000e+21, 'N', 584.7,
    -135689.423169, -32378.937608, 225247.668127, 3.903893698308, -1.466143196544, 2.136277865199);
  ura_api.put_scenario_body('MIRANDA', 4, 'Titania', 'MOON', 3.400000e+21, 'N', 788.4,
    -420374.406258, 66970.986277, 99052.413181, 0.628232852727, -1.084461861863, 3.415815498550);
  ura_api.put_scenario_body('MIRANDA', 5, 'Oberon', 'MOON', 3.076000e+21, 'N', 761.4,
    -567262.238715, 99685.141731, 99731.884352, 0.362993956217, -0.907366185093, 2.989829762549);
  ura_api.put_scenario_body('MIRANDA', 6, 'Francisco', 'TEST', 7.247864e+15, 'J', 11,
    -2722003.400692, 4010116.328049, 403610.744155, 0.664586986561, 0.328359426989, 0.690909024649);
  ura_api.put_scenario_body('MIRANDA', 7, 'Caliban', 'TEST', 2.540619e+17, 'J', 36,
    -3244319.280239, -2281674.241018, -4661267.441506, -0.798068174323, 0.354793562015, 0.571064618825);
  ura_api.put_scenario_body('MIRANDA', 8, 'Stephano', 'TEST', 2.230447e+16, 'J', 16,
    192454.893013, 3810610.933766, 6299881.490083, 0.892378766366, 0.202819014790, 0.090602697449);
  ura_api.put_scenario_body('MIRANDA', 9, 'S/2023 U1', 'TEST', 3.485073e+14, 'J', 4,
    5586600.585079, -3390201.011699, -6622409.311694, -0.307314763692, -0.640392326316, -0.123062074657);
  ura_api.put_scenario_body('MIRANDA', 10, 'Trinculo', 'TEST', 3.969716e+15, 'J', 9,
    -9105172.969318, -4330757.394301, -2385096.800420, -0.297278874558, 0.467002753988, 0.360871931225);
  ura_api.put_scenario_body('MIRANDA', 11, 'Sycorax', 'TEST', 2.297290e+18, 'J', 75,
    408580.798490, 15128521.365508, 6758038.445431, 0.402315340645, 0.233364014318, -0.080455449286);
  ura_api.put_scenario_body('MIRANDA', 12, 'Margaret', 'TEST', 5.445427e+15, 'J', 10,
    10673238.645247, -1617430.465252, -12885408.461788, 0.108706339913, 0.052242461289, 0.532770657311);
  ura_api.put_scenario_body('MIRANDA', 13, 'Prospero', 'TEST', 8.508480e+16, 'J', 25,
    14494631.298480, -17017379.450682, -3814036.251261, -0.322547617024, -0.191184392674, 0.107462686312);
  ura_api.put_scenario_body('MIRANDA', 14, 'Setebos', 'TEST', 7.527759e+16, 'J', 24,
    10271000.796104, -939831.722514, -6117756.344146, -0.576685149252, -0.548464036483, -0.081635715078);
  ura_api.put_scenario_body('MIRANDA', 15, 'Ferdinand', 'TEST', 5.445427e+15, 'J', 10,
    -18510052.287780, -19595396.671044, -8796435.153558, -0.244085879316, 0.198487340458, 0.159169496316);
END;
/

BEGIN
  ura_api.put_scenario(p_code => 'BESUCHER', p_title => 'Besucher',
    p_description => 'Ein Körper von Erdmasse kommt mit 5 km/s aus der Ferne und fliegt 30 Tage nach dem Start in 800 000 km an Uranus vorbei, 30° gegen den Äquator geneigt — gut 200 000 km an Oberons Bahn vorbei.',
    p_start_jd => 2461306.5, p_rate => 10, p_origin => 'BUILTIN', p_compare => 'J');
  ura_api.put_scenario_body('BESUCHER', 1, 'Miranda', 'MOON', 6.400000e+19, 'N', 235.8,
    -106066.532212, 48150.827080, -57418.186562, -3.342627055105, -0.511970265682, 5.760279850001);
  ura_api.put_scenario_body('BESUCHER', 2, 'Ariel', 'MOON', 1.251000e+21, 'N', 578.9,
    161603.262534, -8117.301347, -101681.244042, -2.698070105277, 1.839301634395, -4.430721797068);
  ura_api.put_scenario_body('BESUCHER', 3, 'Umbriel', 'MOON', 1.275000e+21, 'N', 584.7,
    -135689.423169, -32378.937608, 225247.668127, 3.903893698308, -1.466143196544, 2.136277865199);
  ura_api.put_scenario_body('BESUCHER', 4, 'Titania', 'MOON', 3.400000e+21, 'N', 788.4,
    -420374.406258, 66970.986277, 99052.413181, 0.628232852727, -1.084461861863, 3.415815498550);
  ura_api.put_scenario_body('BESUCHER', 5, 'Oberon', 'MOON', 3.076000e+21, 'N', 761.4,
    -567262.238715, 99685.141731, 99731.884352, 0.362993956217, -0.907366185093, 2.989829762549);
  ura_api.put_scenario_body('BESUCHER', 6, 'Francisco', 'TEST', 7.247864e+15, 'J', 11,
    -2722003.400692, 4010116.328049, 403610.744155, 0.664586986561, 0.328359426989, 0.690909024649);
  ura_api.put_scenario_body('BESUCHER', 7, 'Caliban', 'TEST', 2.540619e+17, 'J', 36,
    -3244319.280239, -2281674.241018, -4661267.441506, -0.798068174323, 0.354793562015, 0.571064618825);
  ura_api.put_scenario_body('BESUCHER', 8, 'Stephano', 'TEST', 2.230447e+16, 'J', 16,
    192454.893013, 3810610.933766, 6299881.490083, 0.892378766366, 0.202819014790, 0.090602697449);
  ura_api.put_scenario_body('BESUCHER', 9, 'S/2023 U1', 'TEST', 3.485073e+14, 'J', 4,
    5586600.585079, -3390201.011699, -6622409.311694, -0.307314763692, -0.640392326316, -0.123062074657);
  ura_api.put_scenario_body('BESUCHER', 10, 'Trinculo', 'TEST', 3.969716e+15, 'J', 9,
    -9105172.969318, -4330757.394301, -2385096.800420, -0.297278874558, 0.467002753988, 0.360871931225);
  ura_api.put_scenario_body('BESUCHER', 11, 'Sycorax', 'TEST', 2.297290e+18, 'J', 75,
    408580.798490, 15128521.365508, 6758038.445431, 0.402315340645, 0.233364014318, -0.080455449286);
  ura_api.put_scenario_body('BESUCHER', 12, 'Margaret', 'TEST', 5.445427e+15, 'J', 10,
    10673238.645247, -1617430.465252, -12885408.461788, 0.108706339913, 0.052242461289, 0.532770657311);
  ura_api.put_scenario_body('BESUCHER', 13, 'Prospero', 'TEST', 8.508480e+16, 'J', 25,
    14494631.298480, -17017379.450682, -3814036.251261, -0.322547617024, -0.191184392674, 0.107462686312);
  ura_api.put_scenario_body('BESUCHER', 14, 'Setebos', 'TEST', 7.527759e+16, 'J', 24,
    10271000.796104, -939831.722514, -6117756.344146, -0.576685149252, -0.548464036483, -0.081635715078);
  ura_api.put_scenario_body('BESUCHER', 15, 'Ferdinand', 'TEST', 5.445427e+15, 'J', 10,
    -18510052.287780, -19595396.671044, -8796435.153558, -0.244085879316, 0.198487340458, 0.159169496316);
  ura_api.put_scenario_body('BESUCHER', 16, 'Besucher', 'GUEST', 5.972000e+24, 'N', 6371,
    -1411142.386085, 3826872.429279, 12934337.459549, 0.892886613746, -1.498501166471, -4.782306096622);
END;
/

BEGIN
  ura_api.put_scenario(p_code => 'GEDRAENGE', p_title => 'Gedränge',
    p_description => 'Nur die inneren Monde, mit tausendfach überhöhter Masse (Dichte 1,3 g/cm³ angenommen). So zeigt sich in Minuten, was sonst Jahrmillionen dauert. Ob es wirklich geschieht, ist offen: Frühere Rechnungen sagten Zusammenstöße voraus (Cressida–Desdemona unter 100 Mio. Jahren), eine Arbeit von 2022 findet die Belinda-Gruppe über 10⁸ Jahre stabil.',
    p_start_jd => 2461306.5, p_rate => 30, p_origin => 'BUILTIN', p_compare => 'N');
  ura_api.put_scenario_body('GEDRAENGE', 1, 'Cordelia', 'MOON', 4.356342e+19, 'J', 20,
    -26419.741139, -5637.349729, 41782.503410, 8.865269894305, -3.392740515482, 5.147898123786);
  ura_api.put_scenario_body('GEDRAENGE', 2, 'Ophelia', 'MOON', 5.043010e+19, 'J', 21,
    -37358.074291, 18185.148897, -34898.387765, -7.185353780141, -0.465676291670, 7.353116881688);
  ura_api.put_scenario_body('GEDRAENGE', 3, 'S/2025 U1', 'MOON', 6.806784e+17, 'J', 5,
    55497.103704, -15780.575064, 11362.518950, 2.296099663961, 2.766671120909, -9.168664155613);
  ura_api.put_scenario_body('GEDRAENGE', 4, 'Bianca', 'MOON', 9.570883e+19, 'J', 26,
    42556.890421, 2652.037439, -40569.948452, -6.626600345937, 2.992986503644, -6.795921626924);
  ura_api.put_scenario_body('GEDRAENGE', 5, 'Cressida', 'MOON', 3.485073e+20, 'J', 40,
    -51384.527023, 39.675182, 34690.131155, 5.073178405561, -3.364333367732, 7.492027791944);
  ura_api.put_scenario_body('GEDRAENGE', 6, 'Desdemona', 'MOON', 1.784358e+20, 'J', 32,
    -44479.628066, 22121.792552, -37856.652957, -6.547508961503, -0.978963671340, 7.022566893340);
  ura_api.put_scenario_body('GEDRAENGE', 7, 'Juliet', 'MOON', 5.653606e+20, 'J', 47,
    5993.259295, 14309.427953, -62708.881109, -9.272578360815, 1.779993858628, -0.526088276336);
  ura_api.put_scenario_body('GEDRAENGE', 8, 'Portia', 'MOON', 1.712217e+21, 'J', 68,
    -61063.496716, 16884.019733, -19514.284985, -3.292714510441, -1.965720987053, 8.515014581834);
  ura_api.put_scenario_body('GEDRAENGE', 9, 'Rosalind', 'MOON', 2.540619e+20, 'J', 36,
    61635.123749, -20130.817211, 25809.403349, 3.798773609638, 1.171197873354, -8.212490861463);
  ura_api.put_scenario_body('GEDRAENGE', 10, 'Cupid', 'MOON', 3.969716e+18, 'J', 9,
    69278.771731, -5293.636150, -27222.423007, -2.800840052039, 2.862940336691, -7.836469241387);
  ura_api.put_scenario_body('GEDRAENGE', 11, 'Belinda', 'MOON', 4.962146e+20, 'J', 45,
    62397.992477, -24915.051503, 33951.719354, 4.535218477315, 1.201905127994, -7.414537625902);
  ura_api.put_scenario_body('GEDRAENGE', 12, 'Perdita', 'MOON', 1.837832e+19, 'J', 15,
    73762.979564, -17727.784809, 9012.599898, 1.502106432562, 1.796584768068, -8.391559984205);
  ura_api.put_scenario_body('GEDRAENGE', 13, 'Puck', 'MOON', 2.893923e+21, 'J', 81,
    -71315.044812, 27524.603203, -38993.151970, -4.238084811985, -0.836790601051, 7.002718125167);
  ura_api.put_scenario_body('GEDRAENGE', 14, 'Mab', 'MOON', 9.409698e+18, 'J', 12,
    -94510.509585, 18976.973372, 12081.625643, 0.457007694571, -2.395711355100, 7.353111705834);
END;
/

COMMIT;
