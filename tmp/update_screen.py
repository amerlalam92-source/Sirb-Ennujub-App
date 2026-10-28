import sys

path = 'app/src/main/java/com/example/presentation/evaluation/matrix/EvaluationMatrixScreen.kt'
with open(path, 'r', encoding='utf-8') as f:
    content = f.read()

start_marker = '                                // 1. ترويسة الجدول (Header Row)'
end_marker = '                        }\n                    }'

replacement = """                                // 1. ترويسة الجدول (Header Row)
                                Row(
                                    modifier = Modifier
                                        .height(IntrinsicSize.Min)
                                        .testTag("eval_matrix_header_row")
                                ) {
                                    // 1. الترتيب (50.dp)
                                    MatrixTableCell(
                                        text = stringResource(id = R.string.eval_col_rank),
                                        width = 50.dp,
                                        isHeader = true,
                                        isBold = true
                                    )
                                    // 2. اسم الشبل (140.dp)
                                    MatrixTableCell(
                                        text = stringResource(id = R.string.eval_col_scout),
                                        width = 140.dp,
                                        isHeader = true
                                    )
                                    // 3. تقييم الحضور (85.dp)
                                    MatrixTableCell(
                                        text = stringResource(id = R.string.eval_col_attendance_score),
                                        width = 85.dp,
                                        isHeader = true
                                    )
                                    // 4. تقييم القادة (85.dp)
                                    MatrixTableCell(
                                        text = stringResource(id = R.string.eval_col_leaders),
                                        width = 85.dp,
                                        isHeader = true
                                    )
                                    // 5. تقييم الحفظ (85.dp)
                                    MatrixTableCell(
                                        text = stringResource(id = R.string.eval_col_quran),
                                        width = 85.dp,
                                        isHeader = true
                                    )
                                    // 6. تقييم الكراس (85.dp)
                                    MatrixTableCell(
                                        text = stringResource(id = R.string.eval_col_notebook),
                                        width = 85.dp,
                                        isHeader = true
                                    )
                                    // 7. شارات الكفاية (85.dp)
                                    MatrixTableCell(
                                        text = stringResource(id = R.string.eval_col_kafaa_points),
                                        width = 85.dp,
                                        isHeader = true
                                    )
                                    // 8. شارات الهواية (85.dp)
                                    MatrixTableCell(
                                        text = stringResource(id = R.string.eval_col_hobby_points),
                                        width = 85.dp,
                                        isHeader = true
                                    )
                                    // 9. المعدل العام (90.dp)
                                    MatrixTableCell(
                                        text = stringResource(id = R.string.eval_col_overall),
                                        width = 90.dp,
                                        isHeader = true,
                                        isBold = true
                                    )
                                    // 10. المجموع الكلي (90.dp - MUST BE LAST)
                                    MatrixTableCell(
                                        text = stringResource(id = R.string.eval_col_total_score),
                                        width = 90.dp,
                                        isHeader = true,
                                        isBold = true
                                    )
                                }

                                // 2. صفوف بيانات الأشبال (Data Rows)
                                uiState.filteredRows.forEachIndexed { index, row ->
                                    val isEven = index % 2 == 0
                                    Row(
                                        modifier = Modifier
                                            .height(IntrinsicSize.Min)
                                            .testTag("eval_matrix_row_${row.scoutId}")
                                    ) {
                                        // 1. الترتيب
                                        MatrixTableCell(
                                            text = "${row.rank}",
                                            width = 50.dp,
                                            isHeader = false,
                                            isBold = true,
                                            isAlternate = !isEven,
                                            alignment = Alignment.Center
                                        )

                                        // 2. اسم الشبل
                                        MatrixTableCell(
                                            text = row.scoutName,
                                            width = 140.dp,
                                            isHeader = false,
                                            isAlternate = !isEven,
                                            alignment = Alignment.CenterEnd
                                        )

                                        // 3. تقييم الحضور (من 20)
                                        MatrixScoreCell(
                                            score = row.attendanceScore,
                                            width = 85.dp,
                                            isAlternate = !isEven
                                        )

                                        // 4. تقييم القادة (من 20)
                                        MatrixScoreCell(
                                            score = row.leaderScore,
                                            width = 85.dp,
                                            isAlternate = !isEven
                                        )

                                        // 5. تقييم الحفظ (من 20)
                                        MatrixScoreCell(
                                            score = row.quranScore,
                                            width = 85.dp,
                                            isAlternate = !isEven
                                        )

                                        // 6. تقييم الكراس (من 20)
                                        MatrixScoreCell(
                                            score = row.notebookScore,
                                            width = 85.dp,
                                            isAlternate = !isEven
                                        )

                                        // 7. شارات الكفاية (رقم خام)
                                        MatrixTableCell(
                                            width = 85.dp,
                                            isHeader = false,
                                            isAlternate = !isEven,
                                            alignment = Alignment.Center
                                        ) {
                                            val kafaaText = if (row.kafaaPoints != null) row.kafaaPoints.toString() else "—"
                                            Text(
                                                text = kafaaText,
                                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                                color = if (row.kafaaPoints != null && row.kafaaPoints > 0) Color(0xFF1565C0) else Color(0xFF9E9E9E),
                                                textAlign = TextAlign.Center
                                            )
                                        }

                                        // 8. شارات الهواية (رقم خام)
                                        MatrixTableCell(
                                            width = 85.dp,
                                            isHeader = false,
                                            isAlternate = !isEven,
                                            alignment = Alignment.Center
                                        ) {
                                            val hobbyText = if (row.hobbyPoints != null) row.hobbyPoints.toString() else "—"
                                            Text(
                                                text = hobbyText,
                                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                                color = if (row.hobbyPoints != null && row.hobbyPoints > 0) Color(0xFF1565C0) else Color(0xFF9E9E9E),
                                                textAlign = TextAlign.Center
                                            )
                                        }

                                        // 9. المعدل العام (من 20)
                                        MatrixScoreCell(
                                            score = row.overallScore,
                                            width = 90.dp,
                                            isBold = true,
                                            isAlternate = !isEven
                                        )

                                        // 10. المجموع الكلي (أخضر >=80، عنبري 50-79، أحمر <50)
                                        MatrixTableCell(
                                            width = 90.dp,
                                            isHeader = false,
                                            isBold = true,
                                            isAlternate = !isEven,
                                            alignment = Alignment.Center
                                        ) {
                                            val totalColor = getTotalScoreColor(row.totalScore)
                                            val totalText = if (row.totalScore != null) row.totalScore.toString() else "—"
                                            Surface(
                                                color = totalColor.copy(alpha = 0.15f),
                                                shape = RoundedCornerShape(6.dp),
                                                modifier = Modifier.padding(2.dp)
                                            ) {
                                                Text(
                                                    text = totalText,
                                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                                    color = totalColor,
                                                    textAlign = TextAlign.Center,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // بطاقة شرح طريقة الحساب
                        Spacer(modifier = Modifier.height(24.dp))
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Info,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Text(
                                        text = stringResource(id = R.string.eval_legend_title),
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                HorizontalDivider(
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                )
                                Text(
                                    text = "• " + stringResource(id = R.string.eval_legend_overall),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "• " + stringResource(id = R.string.eval_legend_total),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "• " + stringResource(id = R.string.eval_legend_kafaa),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "• " + stringResource(id = R.string.eval_legend_hobby),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }"""

start_idx = content.find(start_marker)
end_idx = content.find(end_marker, start_idx) + len(end_marker)

if start_idx != -1 and end_idx != -1:
    new_content = content[:start_idx] + replacement + content[end_idx:]
    with open(path, 'w', encoding='utf-8') as f:
        f.write(new_content)
    print('SUCCESS')
else:
    print(f'Failed to find markers: start={start_idx}, end={end_idx}')
    sys.exit(1)
